package com.enterprise.logviewer.aws.eks;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Fetches logs from AWS EKS cluster pods using Kubernetes Java Client.
 * Uses virtual threads for concurrent log fetching from multiple pods.
 */
public class EKSLogFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EKSLogFetcher.class);

    private final CoreV1Api coreApi;
    private final ExecutorService virtualExecutor;
    private final Path logStoragePath;

    /**
     * Create EKS log fetcher using default kubeconfig.
     */
    public EKSLogFetcher(Path logStoragePath) throws IOException {
        this(Config.defaultClient(), logStoragePath);
    }

    /**
     * Create EKS log fetcher with custom API client.
     */
    public EKSLogFetcher(ApiClient apiClient, Path logStoragePath) {
        Configuration.setDefaultApiClient(apiClient);
        this.coreApi = new CoreV1Api(apiClient);
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.logStoragePath = logStoragePath;

        logger.info("EKS log fetcher initialized with storage path: {}", logStoragePath);
    }

    /**
     * Fetch logs from all pods in specified namespace.
     * Uses virtual threads to fetch from multiple pods concurrently.
     */
    public CompletableFuture<List<FetchedLog>> fetchLogsFromNamespace(
            String namespace,
            String labelSelector,
            Instant sinceTime) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Fetching logs from namespace: {} with selector: {}", namespace, labelSelector);

                // List all pods in namespace
                V1PodList podList = coreApi.listNamespacedPod(
                    namespace,
                    null, // pretty
                    null, // allowWatchBookmarks
                    null, // continue
                    null, // fieldSelector
                    labelSelector, // labelSelector
                    null, // limit
                    null, // resourceVersion
                    null, // resourceVersionMatch
                    null, // sendInitialEvents
                    null, // timeoutSeconds
                    null  // watch
                );

                List<V1Pod> pods = podList.getItems();
                logger.info("Found {} pods in namespace {}", pods.size(), namespace);

                // Fetch logs from each pod concurrently using virtual threads
                List<CompletableFuture<List<FetchedLog>>> futures = new ArrayList<>();

                for (V1Pod pod : pods) {
                    CompletableFuture<List<FetchedLog>> future = fetchLogsFromPod(
                        namespace,
                        pod,
                        sinceTime
                    );
                    futures.add(future);
                }

                // Wait for all futures to complete
                return futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            } catch (ApiException e) {
                logger.error("Failed to fetch logs from namespace: {}", namespace, e);
                throw new RuntimeException("Failed to fetch logs from namespace", e);
            }
        }, virtualExecutor);
    }

    /**
     * Fetch logs from a specific pod (all containers).
     */
    public CompletableFuture<List<FetchedLog>> fetchLogsFromPod(
            String namespace,
            V1Pod pod,
            Instant sinceTime) {

        return CompletableFuture.supplyAsync(() -> {
            String podName = pod.getMetadata().getName();
            List<FetchedLog> fetchedLogs = new ArrayList<>();

            try {
                // Get all containers in pod
                List<V1Container> containers = pod.getSpec().getContainers();

                for (V1Container container : containers) {
                    String containerName = container.getName();

                    try {
                        FetchedLog log = fetchContainerLog(
                            namespace,
                            podName,
                            containerName,
                            sinceTime
                        );
                        fetchedLogs.add(log);

                        logger.info("Fetched logs from pod: {}, container: {}", podName, containerName);

                    } catch (Exception e) {
                        logger.error("Failed to fetch logs from pod: {}, container: {}",
                                   podName, containerName, e);
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to process pod: {}", podName, e);
            }

            return fetchedLogs;

        }, virtualExecutor);
    }

    /**
     * Fetch logs from a specific container in a pod.
     */
    private FetchedLog fetchContainerLog(
            String namespace,
            String podName,
            String containerName,
            Instant sinceTime) throws ApiException, IOException {

        // Calculate sinceSeconds from sinceTime
        Integer sinceSeconds = null;
        if (sinceTime != null) {
            long seconds = Instant.now().getEpochSecond() - sinceTime.getEpochSecond();
            sinceSeconds = (int) seconds;
        }

        // Fetch logs from Kubernetes API
        String logs = coreApi.readNamespacedPodLog(
            podName,
            namespace,
            containerName, // container
            null, // follow
            null, // insecureSkipTLSVerifyBackend
            null, // limitBytes
            null, // pretty
            false, // previous
            sinceSeconds, // sinceSeconds
            null, // sinceTime
            null, // tailLines
            null  // timestamps
        );

        // Create storage directory structure: {namespace}/{podName}/
        Path podDir = logStoragePath.resolve(namespace).resolve(podName);
        Files.createDirectories(podDir);

        // Save log to file
        String timestamp = Instant.now().toString().replace(":", "-");
        String filename = String.format("%s_%s.log", containerName, timestamp);
        Path logFile = podDir.resolve(filename);

        Files.writeString(logFile, logs);

        return new FetchedLog(
            namespace,
            podName,
            containerName,
            logFile,
            logs.lines().count(),
            Files.size(logFile),
            Instant.now()
        );
    }

    /**
     * Fetch logs from multiple namespaces concurrently.
     */
    public CompletableFuture<List<FetchedLog>> fetchLogsFromMultipleNamespaces(
            List<String> namespaces,
            String labelSelector,
            Instant sinceTime) {

        List<CompletableFuture<List<FetchedLog>>> futures = namespaces.stream()
            .map(ns -> fetchLogsFromNamespace(ns, labelSelector, sinceTime))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList())
            );
    }

    @Override
    public void close() {
        virtualExecutor.close();
        logger.info("EKS log fetcher closed");
    }

    /**
     * Record representing fetched log metadata.
     */
    public record FetchedLog(
        String namespace,
        String podName,
        String containerName,
        Path logFile,
        long lineCount,
        long sizeBytes,
        Instant fetchedAt
    ) {}
}
