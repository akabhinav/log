package com.enterprise.logviewer.app;

import com.enterprise.logviewer.ui.main.MainWindow;
import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main application entry point for Enterprise Log Viewer.
 * High-Performance Multi-Server Log Analyzer with AWS EKS Integration.
 *
 * Features:
 * - Support for 1GB+ log files with sub-second search
 * - Universal log format parsing (JSON, XML, Syslog, Log4j, etc.)
 * - Apache Lucene indexing for fast search
 * - AWS EKS cluster log fetching
 * - SSH/SFTP remote log fetching
 * - Virtual threading for concurrent operations
 * - Dual-pane log comparison
 * - Live debugging with variable inspection
 *
 * @version 1.0.0
 */
public class LogViewerApplication {

    private static final Logger logger = LoggerFactory.getLogger(LogViewerApplication.class);

    private static final String APP_NAME = "Enterprise Log Viewer";
    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        logger.info("Starting {} v{}", APP_NAME, VERSION);
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("Java Virtual Threads: {}", supportsVirtualThreads() ? "Supported" : "Not Supported");

        // Verify Java 21
        if (!isJava21OrHigher()) {
            System.err.println("ERROR: Java 21 or higher is required!");
            System.err.println("Current version: " + System.getProperty("java.version"));
            System.exit(1);
        }

        // Create application directories
        createAppDirectories();

        // Set system properties for better performance
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",
                          String.valueOf(Runtime.getRuntime().availableProcessors()));

        // Set FlatLaf dark theme
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            logger.info("FlatLaf Dark theme enabled");
        } catch (Exception e) {
            logger.error("Failed to set look and feel, using default", e);
        }

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                MainWindow mainWindow = new MainWindow();
                mainWindow.setVisible(true);
                logger.info("{} started successfully", APP_NAME);
            } catch (Exception e) {
                logger.error("Failed to start application", e);
                JOptionPane.showMessageDialog(null,
                    "Failed to start application: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static boolean isJava21OrHigher() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        return major >= 21;
    }

    private static boolean supportsVirtualThreads() {
        try {
            // Try to create a virtual thread to verify support
            Thread.ofVirtual().start(() -> {}).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void createAppDirectories() {
        try {
            Path appHome = Path.of(System.getProperty("user.home"), ".logviewer");
            Path indexDir = appHome.resolve("index");
            Path logsDir = appHome.resolve("logs");
            Path configDir = appHome.resolve("config");

            Files.createDirectories(indexDir);
            Files.createDirectories(logsDir);
            Files.createDirectories(configDir);

            logger.info("Application directory: {}", appHome);
            logger.info("Index directory: {}", indexDir);
            logger.info("Logs directory: {}", logsDir);

        } catch (Exception e) {
            logger.error("Failed to create application directories", e);
        }
    }
}
