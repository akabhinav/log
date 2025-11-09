package com.enterprise.logviewer.aws.s3;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches log files from remote servers via SSH/SFTP.
 * Uses virtual threads for concurrent fetching from multiple servers.
 */
public class SshLogFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SshLogFetcher.class);

    private final ExecutorService virtualExecutor;
    private final Path localStoragePath;
    private final JSch jsch;

    public SshLogFetcher(Path localStoragePath) {
        this.localStoragePath = localStoragePath;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.jsch = new JSch();

        logger.info("SSH log fetcher initialized with storage: {}", localStoragePath);
    }

    /**
     * Add SSH private key for authentication.
     */
    public void addIdentity(Path privateKeyPath, String passphrase) throws JSchException {
        if (passphrase != null) {
            jsch.addIdentity(privateKeyPath.toString(), passphrase);
        } else {
            jsch.addIdentity(privateKeyPath.toString());
        }
        logger.info("Added SSH identity: {}", privateKeyPath);
    }

    /**
     * Fetch log file from remote server via SFTP.
     */
    public CompletableFuture<FetchedLog> fetchLogFile(
            String host,
            int port,
            String username,
            String password,
            String remoteFilePath) {

        return CompletableFuture.supplyAsync(() -> {
            Session session = null;
            ChannelSftp sftpChannel = null;

            try {
                logger.info("Connecting to {}:{} as {}", host, port, username);

                // Create session
                session = jsch.getSession(username, host, port);

                if (password != null) {
                    session.setPassword(password);
                }

                // Disable strict host key checking (use with caution)
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);

                session.connect(30000); // 30 second timeout

                // Open SFTP channel
                Channel channel = session.openChannel("sftp");
                channel.connect();
                sftpChannel = (ChannelSftp) channel;

                // Create local directory for server
                Path serverDir = localStoragePath.resolve(host);
                Files.createDirectories(serverDir);

                // Download file
                String filename = Path.of(remoteFilePath).getFileName().toString();
                String timestamp = Instant.now().toString().replace(":", "-");
                String localFilename = filename + "_" + timestamp;
                Path localFile = serverDir.resolve(localFilename);

                logger.info("Downloading {} to {}", remoteFilePath, localFile);

                try (InputStream inputStream = sftpChannel.get(remoteFilePath)) {
                    Files.copy(inputStream, localFile, StandardCopyOption.REPLACE_EXISTING);
                }

                long fileSize = Files.size(localFile);
                long lineCount = Files.lines(localFile).count();

                logger.info("Downloaded {} ({} bytes, {} lines)", localFile, fileSize, lineCount);

                return new FetchedLog(
                    host,
                    remoteFilePath,
                    localFile,
                    lineCount,
                    fileSize,
                    Instant.now()
                );

            } catch (JSchException | SftpException | IOException e) {
                logger.error("Failed to fetch log from {}:{}", host, remoteFilePath, e);
                throw new RuntimeException("Failed to fetch log via SSH", e);

            } finally {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }
        }, virtualExecutor);
    }

    /**
     * Fetch logs from multiple remote servers concurrently.
     */
    public CompletableFuture<List<FetchedLog>> fetchFromMultipleServers(
            List<ServerConfig> servers) {

        List<CompletableFuture<FetchedLog>> futures = servers.stream()
            .map(config -> fetchLogFile(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.remoteFilePath()
            ))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<FetchedLog> results = new ArrayList<>();
                for (CompletableFuture<FetchedLog> future : futures) {
                    try {
                        results.add(future.join());
                    } catch (Exception e) {
                        logger.error("Failed to fetch from server", e);
                    }
                }
                return results;
            });
    }

    /**
     * Execute remote command via SSH and capture output.
     */
    public CompletableFuture<String> executeRemoteCommand(
            String host,
            int port,
            String username,
            String password,
            String command) {

        return CompletableFuture.supplyAsync(() -> {
            Session session = null;
            ChannelExec execChannel = null;

            try {
                session = jsch.getSession(username, host, port);

                if (password != null) {
                    session.setPassword(password);
                }

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);

                session.connect(30000);

                execChannel = (ChannelExec) session.openChannel("exec");
                execChannel.setCommand(command);

                InputStream in = execChannel.getInputStream();
                execChannel.connect();

                StringBuilder output = new StringBuilder();
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, bytesRead));
                }

                execChannel.disconnect();
                session.disconnect();

                return output.toString();

            } catch (JSchException | IOException e) {
                logger.error("Failed to execute command on {}: {}", host, command, e);
                throw new RuntimeException("Failed to execute remote command", e);

            } finally {
                if (execChannel != null && execChannel.isConnected()) {
                    execChannel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }
        }, virtualExecutor);
    }

    @Override
    public void close() {
        virtualExecutor.close();
        logger.info("SSH log fetcher closed");
    }

    /**
     * Server configuration record.
     */
    public record ServerConfig(
        String host,
        int port,
        String username,
        String password,
        String remoteFilePath
    ) {
        public ServerConfig(String host, String username, String password, String remoteFilePath) {
            this(host, 22, username, password, remoteFilePath);
        }
    }

    /**
     * Fetched log metadata record.
     */
    public record FetchedLog(
        String serverHost,
        String remoteFilePath,
        Path localFile,
        long lineCount,
        long sizeBytes,
        Instant fetchedAt
    ) {}
}
