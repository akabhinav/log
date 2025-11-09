package com.enterprise.logviewer.ui.dialogs;

import com.enterprise.logviewer.aws.auth.AwsPclAuthService;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * AWS PCL Login Dialog with password and MFA token input.
 * Provides a user-friendly interface for AWS authentication.
 */
public class AwsPclLoginDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(AwsPclLoginDialog.class);

    private final AwsPclAuthService authService;

    // UI Components
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JTextField mfaTokenField;
    private final JButton loginButton;
    private final JButton submitMfaButton;
    private final JButton downloadLogsButton;
    private final JButton cancelButton;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    // EKS Configuration
    private final JTextField clusterNameField;
    private final JTextField namespaceField;
    private final JTextField podPatternField;
    private final JTextField downloadPathField;

    // State
    private String sessionId;
    private boolean authenticated = false;

    public AwsPclLoginDialog(Frame owner, AwsPclAuthService authService) {
        super(owner, "AWS PCL Login", true);
        this.authService = authService;

        // Initialize components
        this.usernameField = new JTextField(20);
        this.passwordField = new JPasswordField(20);
        this.mfaTokenField = new JTextField(6);
        this.loginButton = new JButton("Login");
        this.submitMfaButton = new JButton("Submit MFA Token");
        this.downloadLogsButton = new JButton("Download Logs");
        this.cancelButton = new JButton("Cancel");
        this.statusLabel = new JLabel("Enter your credentials");
        this.progressBar = new JProgressBar();

        // EKS fields
        this.clusterNameField = new JTextField(20);
        this.namespaceField = new JTextField(20);
        this.podPatternField = new JTextField(20);
        this.downloadPathField = new JTextField(30);

        // Set defaults
        namespaceField.setText("default");
        podPatternField.setText(".*");
        downloadPathField.setText(Paths.get(System.getProperty("user.home"), "eks-logs").toString());

        initUI();
        setupEventHandlers();

        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Main panel
        JPanel mainPanel = new JPanel(new MigLayout("fillx, insets 15", "[right]15[fill,grow]", ""));

        // === Step 1: Credentials ===
        mainPanel.add(new JLabel("Step 1: AWS PCL Login"), "span, gapbottom 10, wrap");

        mainPanel.add(new JLabel("Username:"));
        mainPanel.add(usernameField, "wrap");

        mainPanel.add(new JLabel("Password:"));
        mainPanel.add(passwordField, "wrap");

        passwordField.setToolTipText("Password is hardcoded for security");

        mainPanel.add(new JLabel(""));
        mainPanel.add(loginButton, "split 2, align right");
        mainPanel.add(cancelButton, "wrap");

        mainPanel.add(new JSeparator(), "span, growx, gaptop 10, gapbottom 10, wrap");

        // === Step 2: MFA Token ===
        mainPanel.add(new JLabel("Step 2: MFA Token"), "span, gapbottom 10, wrap");

        mainPanel.add(new JLabel("MFA Token (6 digits):"));
        mainPanel.add(mfaTokenField, "wrap");

        mfaTokenField.setEnabled(false);
        submitMfaButton.setEnabled(false);

        mainPanel.add(new JLabel(""));
        mainPanel.add(submitMfaButton, "align right, wrap");

        mainPanel.add(new JSeparator(), "span, growx, gaptop 10, gapbottom 10, wrap");

        // === Step 3: Download Logs ===
        mainPanel.add(new JLabel("Step 3: Download EKS Logs"), "span, gapbottom 10, wrap");

        mainPanel.add(new JLabel("Cluster Name:"));
        mainPanel.add(clusterNameField, "wrap");

        mainPanel.add(new JLabel("Namespace:"));
        mainPanel.add(namespaceField, "wrap");

        mainPanel.add(new JLabel("Pod Pattern:"));
        mainPanel.add(podPatternField, "wrap");

        mainPanel.add(new JLabel("Download Path:"));
        mainPanel.add(downloadPathField, "wrap");

        clusterNameField.setEnabled(false);
        namespaceField.setEnabled(false);
        podPatternField.setEnabled(false);
        downloadPathField.setEnabled(false);
        downloadLogsButton.setEnabled(false);

        mainPanel.add(new JLabel(""));
        mainPanel.add(downloadLogsButton, "align right, wrap");

        add(mainPanel, BorderLayout.CENTER);

        // Status panel
        JPanel statusPanel = new JPanel(new MigLayout("fillx, insets 10"));
        statusPanel.add(statusLabel, "growx, pushx");
        statusPanel.add(progressBar, "width 150!");

        progressBar.setVisible(false);

        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        loginButton.addActionListener(e -> handlePasswordLogin());
        submitMfaButton.addActionListener(e -> handleMfaSubmit());
        downloadLogsButton.addActionListener(e -> handleDownloadLogs());
        cancelButton.addActionListener(e -> handleCancel());

        // Enter key handlers
        usernameField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> handlePasswordLogin());
        mfaTokenField.addActionListener(e -> handleMfaSubmit());
    }

    private void handlePasswordLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) {
            showError("Please enter username");
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter password");
            return;
        }

        setStatus("Authenticating...");
        showProgress(true);
        loginButton.setEnabled(false);

        authService.authenticateWithPassword(username, password)
            .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                showProgress(false);

                if (result.success()) {
                    sessionId = result.sessionId();
                    setStatus(result.message());

                    // Enable MFA input
                    mfaTokenField.setEnabled(true);
                    submitMfaButton.setEnabled(true);
                    mfaTokenField.requestFocus();

                    // Disable password fields
                    usernameField.setEnabled(false);
                    passwordField.setEnabled(false);

                    showInfo("Password accepted! Please enter your 6-digit MFA token from your mobile device.");

                } else {
                    showError(result.message());
                    loginButton.setEnabled(true);
                }
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    showProgress(false);
                    showError("Authentication failed: " + ex.getMessage());
                    loginButton.setEnabled(true);
                });
                return null;
            });
    }

    private void handleMfaSubmit() {
        String username = usernameField.getText().trim();
        String mfaToken = mfaTokenField.getText().trim();

        if (mfaToken.isEmpty() || !mfaToken.matches("\\d{6}")) {
            showError("Please enter a valid 6-digit MFA token");
            return;
        }

        setStatus("Validating MFA token...");
        showProgress(true);
        submitMfaButton.setEnabled(false);

        authService.authenticateWithMfaToken(username, sessionId, mfaToken)
            .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                showProgress(false);

                if (result.success()) {
                    authenticated = true;
                    setStatus("✓ Authentication successful!");

                    // Disable MFA fields
                    mfaTokenField.setEnabled(false);

                    // Enable download fields
                    clusterNameField.setEnabled(true);
                    namespaceField.setEnabled(true);
                    podPatternField.setEnabled(true);
                    downloadPathField.setEnabled(true);
                    downloadLogsButton.setEnabled(true);

                    showSuccess("Authentication successful!\n" +
                              "AWS credentials configured.\n" +
                              "kubectl context configured.\n" +
                              "You can now download logs from EKS clusters.");

                } else {
                    showError(result.message());
                    submitMfaButton.setEnabled(true);
                }
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    showProgress(false);
                    showError("MFA validation failed: " + ex.getMessage());
                    submitMfaButton.setEnabled(true);
                });
                return null;
            });
    }

    private void handleDownloadLogs() {
        String clusterName = clusterNameField.getText().trim();
        String namespace = namespaceField.getText().trim();
        String podPattern = podPatternField.getText().trim();
        String downloadPathStr = downloadPathField.getText().trim();

        if (clusterName.isEmpty()) {
            showError("Please enter cluster name");
            return;
        }

        if (namespace.isEmpty()) {
            showError("Please enter namespace");
            return;
        }

        Path downloadPath = Paths.get(downloadPathStr);

        setStatus("Downloading logs from EKS cluster...");
        showProgress(true);
        downloadLogsButton.setEnabled(false);

        authService.downloadLogsFromEks(clusterName, namespace, podPattern, downloadPath)
            .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                showProgress(false);
                downloadLogsButton.setEnabled(true);

                if (result.success()) {
                    setStatus("✓ Logs downloaded successfully!");

                    showSuccess("Logs downloaded successfully!\n" +
                              result.message() + "\n" +
                              "Location: " + result.downloadPath());

                    // Ask if user wants to index the logs
                    int choice = JOptionPane.showConfirmDialog(
                        this,
                        "Would you like to index the downloaded logs for searching?",
                        "Index Logs",
                        JOptionPane.YES_NO_OPTION
                    );

                    if (choice == JOptionPane.YES_OPTION) {
                        // Signal to parent to index logs
                        firePropertyChange("logs-downloaded", null, result.downloadPath());
                    }

                } else {
                    showError(result.message());
                }
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    showProgress(false);
                    downloadLogsButton.setEnabled(true);
                    showError("Download failed: " + ex.getMessage());
                });
                return null;
            });
    }

    private void handleCancel() {
        dispose();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        logger.info("Status: {}", message);
    }

    private void showProgress(boolean show) {
        progressBar.setVisible(show);
        if (show) {
            progressBar.setIndeterminate(true);
        }
    }

    private void showError(String message) {
        statusLabel.setText("✗ " + message);
        statusLabel.setForeground(Color.RED);
        logger.error(message);
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(
            this,
            message,
            "Success",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(
            this,
            message,
            "Information",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Show login dialog and return authentication status.
     */
    public static boolean showLoginDialog(Frame owner, AwsPclAuthService authService) {
        AwsPclLoginDialog dialog = new AwsPclLoginDialog(owner, authService);
        dialog.setVisible(true);
        return dialog.isAuthenticated();
    }
}
