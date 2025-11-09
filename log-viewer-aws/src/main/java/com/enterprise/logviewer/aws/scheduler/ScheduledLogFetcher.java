package com.enterprise.logviewer.aws.scheduler;

import com.enterprise.logviewer.aws.eks.EKSLogFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Scheduled log fetching service with configurable intervals.
 * Uses ScheduledExecutorService with virtual threads for I/O operations.
 */
public class ScheduledLogFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledLogFetcher.class);

    private final EKSLogFetcher eksFetcher;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService virtualExecutor;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks;
    private final ConcurrentHashMap<String, Instant> lastFetchTimes;

    public ScheduledLogFetcher(EKSLogFetcher eksFetcher) {
        this.eksFetcher = eksFetcher;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.lastFetchTimes = new ConcurrentHashMap<>();

        logger.info("Scheduled log fetcher initialized");
    }

    /**
     * Schedule periodic log fetching from namespace.
     *
     * @param scheduleId Unique identifier for this schedule
     * @param namespace Kubernetes namespace
     * @param labelSelector Pod label selector
     * @param interval Fetch interval
     * @param onFetchComplete Callback when fetch completes
     */
    public void scheduleNamespaceFetch(
            String scheduleId,
            String namespace,
            String labelSelector,
            Duration interval,
            Consumer<List<EKSLogFetcher.FetchedLog>> onFetchComplete) {

        // Cancel existing schedule if present
        cancelSchedule(scheduleId);

        logger.info("Scheduling log fetch for namespace {} every {}", namespace, interval);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    logger.info("Running scheduled fetch for namespace: {}", namespace);

                    // Get last fetch time for incremental fetching
                    Instant sinceTime = lastFetchTimes.getOrDefault(
                        scheduleId,
                        Instant.now().minus(interval)
                    );

                    // Execute fetch in virtual thread
                    CompletableFuture<List<EKSLogFetcher.FetchedLog>> fetchFuture =
                        eksFetcher.fetchLogsFromNamespace(namespace, labelSelector, sinceTime);

                    // Wait for completion and invoke callback
                    fetchFuture.thenAccept(fetchedLogs -> {
                        lastFetchTimes.put(scheduleId, Instant.now());
                        logger.info("Fetched {} logs for schedule: {}", fetchedLogs.size(), scheduleId);

                        if (onFetchComplete != null) {
                            onFetchComplete.accept(fetchedLogs);
                        }
                    }).exceptionally(ex -> {
                        logger.error("Scheduled fetch failed for {}", scheduleId, ex);
                        return null;
                    });

                } catch (Exception e) {
                    logger.error("Error in scheduled fetch for {}", scheduleId, e);
                }
            },
            0, // initial delay
            interval.toSeconds(),
            TimeUnit.SECONDS
        );

        scheduledTasks.put(scheduleId, future);
        logger.info("Schedule created: {} for namespace: {}", scheduleId, namespace);
    }

    /**
     * Schedule multi-namespace fetch.
     */
    public void scheduleMultiNamespaceFetch(
            String scheduleId,
            List<String> namespaces,
            String labelSelector,
            Duration interval,
            Consumer<List<EKSLogFetcher.FetchedLog>> onFetchComplete) {

        cancelSchedule(scheduleId);

        logger.info("Scheduling multi-namespace fetch for {} namespaces every {}",
                   namespaces.size(), interval);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    Instant sinceTime = lastFetchTimes.getOrDefault(
                        scheduleId,
                        Instant.now().minus(interval)
                    );

                    CompletableFuture<List<EKSLogFetcher.FetchedLog>> fetchFuture =
                        eksFetcher.fetchLogsFromMultipleNamespaces(namespaces, labelSelector, sinceTime);

                    fetchFuture.thenAccept(fetchedLogs -> {
                        lastFetchTimes.put(scheduleId, Instant.now());
                        logger.info("Multi-namespace fetch completed: {} logs", fetchedLogs.size());

                        if (onFetchComplete != null) {
                            onFetchComplete.accept(fetchedLogs);
                        }
                    }).exceptionally(ex -> {
                        logger.error("Multi-namespace fetch failed", ex);
                        return null;
                    });

                } catch (Exception e) {
                    logger.error("Error in multi-namespace scheduled fetch", e);
                }
            },
            0,
            interval.toSeconds(),
            TimeUnit.SECONDS
        );

        scheduledTasks.put(scheduleId, future);
    }

    /**
     * Schedule one-time fetch (executes after delay).
     */
    public void scheduleOneTimeFetch(
            String scheduleId,
            String namespace,
            String labelSelector,
            Duration delay,
            Consumer<List<EKSLogFetcher.FetchedLog>> onFetchComplete) {

        logger.info("Scheduling one-time fetch for namespace {} after {}", namespace, delay);

        ScheduledFuture<?> future = scheduler.schedule(
            () -> {
                try {
                    CompletableFuture<List<EKSLogFetcher.FetchedLog>> fetchFuture =
                        eksFetcher.fetchLogsFromNamespace(namespace, labelSelector, null);

                    fetchFuture.thenAccept(fetchedLogs -> {
                        logger.info("One-time fetch completed: {} logs", fetchedLogs.size());

                        if (onFetchComplete != null) {
                            onFetchComplete.accept(fetchedLogs);
                        }

                        // Remove from scheduled tasks
                        scheduledTasks.remove(scheduleId);
                    });

                } catch (Exception e) {
                    logger.error("One-time fetch failed", e);
                }
            },
            delay.toSeconds(),
            TimeUnit.SECONDS
        );

        scheduledTasks.put(scheduleId, future);
    }

    /**
     * Cancel a scheduled task.
     */
    public void cancelSchedule(String scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
            lastFetchTimes.remove(scheduleId);
            logger.info("Cancelled schedule: {}", scheduleId);
        }
    }

    /**
     * Cancel all scheduled tasks.
     */
    public void cancelAll() {
        logger.info("Cancelling all {} scheduled tasks", scheduledTasks.size());
        scheduledTasks.forEach((id, future) -> future.cancel(false));
        scheduledTasks.clear();
        lastFetchTimes.clear();
    }

    /**
     * Get active schedule IDs.
     */
    public List<String> getActiveSchedules() {
        return List.copyOf(scheduledTasks.keySet());
    }

    /**
     * Check if schedule is active.
     */
    public boolean isScheduleActive(String scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.get(scheduleId);
        return future != null && !future.isDone();
    }

    /**
     * Get schedule statistics.
     */
    public ScheduleStats getScheduleStats(String scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.get(scheduleId);
        Instant lastFetch = lastFetchTimes.get(scheduleId);

        if (future == null) {
            return null;
        }

        return new ScheduleStats(
            scheduleId,
            !future.isDone(),
            lastFetch,
            future.getDelay(TimeUnit.SECONDS)
        );
    }

    @Override
    public void close() {
        cancelAll();
        scheduler.shutdown();
        virtualExecutor.close();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Scheduled log fetcher closed");
    }

    /**
     * Schedule statistics record.
     */
    public record ScheduleStats(
        String scheduleId,
        boolean active,
        Instant lastFetchTime,
        long nextRunInSeconds
    ) {}
}
