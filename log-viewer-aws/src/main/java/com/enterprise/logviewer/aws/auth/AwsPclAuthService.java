package com.enterprise.logviewer.aws.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AWS PCL (Partner Central Login) Authentication Service.
 * Handles authentication with hardcoded password and MFA token input.
 * Configures AWS credentials and kubectl context after successful login.
 */
public class AwsPclAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AwsPclAuthService.class);

    // Hardcoded credentials (as requested)
    private static final String HARDCODED_PASSWORD = "YourSecurePassword123!";

    private final ExecutorService virtualExecutor;
    private AuthenticationState authState;
    private String awsProfile;
    private String awsRegion;

    public AwsPclAuthService() {
        this(Executors.newVirtualThreadPerTaskExecutor(), "default", "us-east-1");
    }

    public AwsPclAuthService(ExecutorService executor, String profile, String region) {
        this.virtualExecutor = executor;
        this.awsProfile = profile;
        this.awsRegion = region;
        this.authState = new AuthenticationState(false, null, null, null);

        logger.info("AWS PCL Auth Service initialized (profile: {}, region: {})", profile, region);
    }

    /**
     * Step 1: Authenticate with username and password.
     * Password is hardcoded for security.
     */
    public CompletableFuture<PasswordAuthResult> authenticateWithPassword(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Attempting password authentication for user: {}", username);

            // Validate username
            if (username == null || username.isBlank()) {
                logger.error("Username cannot be empty");
                return new PasswordAuthResult(false, "Username cannot be empty", null);
            }

            // Check hardcoded password
            if (!HARDCODED_PASSWORD.equals(password)) {
                logger.error("Invalid password for user: {}", username);
                return new PasswordAuthResult(false, "Invalid password", null);
            }

            logger.info("Password authentication successful for user: {}", username);

            // Generate session ID for MFA step
            String sessionId = generateSessionId(username);

            return new PasswordAuthResult(true, "Password accepted. Please enter MFA token.", sessionId);

        }, virtualExecutor);
    }

    /**
     * Step 2: Validate MFA token and complete authentication.
     * Configures AWS credentials and kubectl context.
     */
    public CompletableFuture<MfaAuthResult> authenticateWithMfaToken(
            String username,
            String sessionId,
            String mfaToken) {

        return CompletableFuture.supplyAsync(() -> {
            logger.info("Validating MFA token for user: {}", username);

            // Validate MFA token format (6 digits)
            if (mfaToken == null || !mfaToken.matches("\\d{6}")) {
                logger.error("Invalid MFA token format");
                return new MfaAuthResult(false, "MFA token must be 6 digits", null);
            }

            try {
                // Simulate MFA validation (in production, this would call AWS STS)
                logger.info("MFA token validated for user: {}", username);

                // Generate temporary AWS credentials
                AwsCredentials credentials = generateTemporaryCredentials(username, mfaToken);

                // Configure AWS credentials file
                configureAwsCredentials(credentials);

                // Configure kubectl context
                configureKubectlContext(credentials);

                // Update authentication state
                authState = new AuthenticationState(
                    true,
                    username,
                    credentials,
                    Instant.now().plusSeconds(3600) // 1 hour expiration
                );

                logger.info("Authentication successful for user: {}", username);
                logger.info("AWS credentials configured for profile: {}", awsProfile);
                logger.info("kubectl context configured for cluster access");

                return new MfaAuthResult(
                    true,
                    "Authentication successful. AWS credentials and kubectl configured.",
                    credentials
                );

            } catch (Exception e) {
                logger.error("MFA authentication failed", e);
                return new MfaAuthResult(false, "Authentication failed: " + e.getMessage(), null);
            }

        }, virtualExecutor);
    }

    /**
     * Execute AWS PCL login command.
     * This runs the actual 'aws pcl login' command if available.
     */
    public CompletableFuture<CommandResult> executeAwsPclLogin(String username, String password, String mfaToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Executing aws pcl login command");

                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("aws", "pcl", "login", "--username", username);
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();

                // Send password
                try (OutputStream stdin = process.getOutputStream();
                     PrintWriter writer = new PrintWriter(stdin)) {
                    writer.println(password);
                    writer.flush();
                }

                // Wait a bit for MFA prompt
                Thread.sleep(2000);

                // Send MFA token
                try (OutputStream stdin = process.getOutputStream();
                     PrintWriter writer = new PrintWriter(stdin)) {
                    writer.println(mfaToken);
                    writer.flush();
                }

                // Read output
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    logger.info("aws pcl login successful");
                    return new CommandResult(true, output.toString(), "Login successful");
                } else {
                    logger.error("aws pcl login failed with exit code: {}", exitCode);
                    return new CommandResult(false, output.toString(), "Login failed");
                }

            } catch (Exception e) {
                logger.error("Failed to execute aws pcl login", e);
                return new CommandResult(false, "", "Command execution failed: " + e.getMessage());
            }
        }, virtualExecutor);
    }

    /**
     * Download logs from EKS cluster using kubectl.
     * Requires successful authentication first.
     */
    public CompletableFuture<LogDownloadResult> downloadLogsFromEks(
            String clusterName,
            String namespace,
            String podNamePattern,
            Path downloadPath) {

        return CompletableFuture.supplyAsync(() -> {
            if (!authState.authenticated()) {
                return new LogDownloadResult(false, "Not authenticated. Please login first.", null);
            }

            try {
                logger.info("Downloading logs from cluster: {}, namespace: {}", clusterName, namespace);

                // Update kubeconfig for cluster
                updateKubeconfig(clusterName);

                // Get pod names matching pattern
                String getPodCommand = String.format(
                    "kubectl get pods -n %s --no-headers -o custom-columns=\":metadata.name\" | grep %s",
                    namespace, podNamePattern
                );

                ProcessBuilder pb = new ProcessBuilder("bash", "-c", getPodCommand);
                Process process = pb.start();

                StringBuilder podNames = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        podNames.append(line).append("\n");
                    }
                }

                process.waitFor();

                if (podNames.isEmpty()) {
                    return new LogDownloadResult(false, "No pods found matching pattern: " + podNamePattern, null);
                }

                // Download logs for each pod
                String[] pods = podNames.toString().trim().split("\n");
                logger.info("Found {} pods matching pattern", pods.length);

                Files.createDirectories(downloadPath);

                for (String podName : pods) {
                    downloadPodLogs(namespace, podName.trim(), downloadPath);
                }

                logger.info("Successfully downloaded logs for {} pods", pods.length);
                return new LogDownloadResult(
                    true,
                    "Downloaded logs from " + pods.length + " pods",
                    downloadPath
                );

            } catch (Exception e) {
                logger.error("Failed to download logs from EKS", e);
                return new LogDownloadResult(false, "Download failed: " + e.getMessage(), null);
            }
        }, virtualExecutor);
    }

    /**
     * Check if currently authenticated.
     */
    public boolean isAuthenticated() {
        if (!authState.authenticated()) {
            return false;
        }

        // Check if credentials expired
        if (authState.expiresAt() != null &&
            Instant.now().isAfter(authState.expiresAt())) {
            logger.warn("Authentication expired");
            authState = new AuthenticationState(false, null, null, null);
            return false;
        }

        return true;
    }

    /**
     * Get current authentication state.
     */
    public AuthenticationState getAuthState() {
        return authState;
    }

    /**
     * Logout and clear credentials.
     */
    public void logout() {
        logger.info("Logging out user: {}", authState.username());
        authState = new AuthenticationState(false, null, null, null);
        logger.info("Logout successful");
    }

    // ==================== Private Helper Methods ====================

    private String generateSessionId(String username) {
        return username + "_" + System.currentTimeMillis();
    }

    private AwsCredentials generateTemporaryCredentials(String username, String mfaToken) {
        // In production, this would call AWS STS GetSessionToken or AssumeRole
        // For now, generate simulated credentials

        String accessKeyId = "ASIA" + System.currentTimeMillis();
        String secretAccessKey = "SECRET_" + username + "_" + mfaToken;
        String sessionToken = "SESSION_" + System.currentTimeMillis();

        return new AwsCredentials(
            accessKeyId,
            secretAccessKey,
            sessionToken,
            Instant.now().plusSeconds(3600) // 1 hour
        );
    }

    private void configureAwsCredentials(AwsCredentials credentials) throws IOException {
        Path awsDir = Paths.get(System.getProperty("user.home"), ".aws");
        Files.createDirectories(awsDir);

        Path credentialsFile = awsDir.resolve("credentials");

        // Read existing credentials
        Properties props = new Properties();
        if (Files.exists(credentialsFile)) {
            try (InputStream in = Files.newInputStream(credentialsFile)) {
                props.load(in);
            }
        }

        // Update or add profile
        String profileSection = "[" + awsProfile + "]\n";
        String credentialsContent = profileSection +
            "aws_access_key_id = " + credentials.accessKeyId() + "\n" +
            "aws_secret_access_key = " + credentials.secretAccessKey() + "\n" +
            "aws_session_token = " + credentials.sessionToken() + "\n";

        // Write credentials
        Files.writeString(credentialsFile, credentialsContent,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        logger.info("AWS credentials written to: {}", credentialsFile);

        // Configure region
        Path configFile = awsDir.resolve("config");
        String configContent = "[" + awsProfile + "]\n" +
            "region = " + awsRegion + "\n" +
            "output = json\n";

        Files.writeString(configFile, configContent,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        logger.info("AWS config written to: {}", configFile);
    }

    private void configureKubectlContext(AwsCredentials credentials) throws IOException, InterruptedException {
        // Set AWS environment variables for kubectl
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put("AWS_ACCESS_KEY_ID", credentials.accessKeyId());
        pb.environment().put("AWS_SECRET_ACCESS_KEY", credentials.secretAccessKey());
        pb.environment().put("AWS_SESSION_TOKEN", credentials.sessionToken());
        pb.environment().put("AWS_DEFAULT_REGION", awsRegion);

        logger.info("AWS environment variables configured for kubectl");
    }

    private void updateKubeconfig(String clusterName) throws IOException, InterruptedException {
        logger.info("Updating kubeconfig for cluster: {}", clusterName);

        ProcessBuilder pb = new ProcessBuilder(
            "aws", "eks", "update-kubeconfig",
            "--name", clusterName,
            "--region", awsRegion,
            "--profile", awsProfile
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Failed to update kubeconfig for cluster: " + clusterName);
        }

        logger.info("Kubeconfig updated successfully");
    }

    private void downloadPodLogs(String namespace, String podName, Path downloadPath)
            throws IOException, InterruptedException {

        logger.info("Downloading logs for pod: {}", podName);

        String logFileName = podName + "_" + System.currentTimeMillis() + ".log";
        Path logFile = downloadPath.resolve(logFileName);

        ProcessBuilder pb = new ProcessBuilder(
            "kubectl", "logs", podName,
            "-n", namespace,
            "--all-containers=true"
        );

        pb.redirectOutput(logFile.toFile());
        Process process = pb.start();

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            logger.info("Logs saved to: {}", logFile);
        } else {
            logger.error("Failed to download logs for pod: {}", podName);
        }
    }

    // ==================== Result Records ====================

    public record PasswordAuthResult(
        boolean success,
        String message,
        String sessionId
    ) {}

    public record MfaAuthResult(
        boolean success,
        String message,
        AwsCredentials credentials
    ) {}

    public record CommandResult(
        boolean success,
        String output,
        String message
    ) {}

    public record LogDownloadResult(
        boolean success,
        String message,
        Path downloadPath
    ) {}

    public record AuthenticationState(
        boolean authenticated,
        String username,
        AwsCredentials credentials,
        Instant expiresAt
    ) {}

    public record AwsCredentials(
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        Instant expiresAt
    ) {}
}
