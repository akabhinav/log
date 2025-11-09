package com.enterprise.logviewer.parsers.detector;

import com.enterprise.logviewer.core.domain.LogFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Auto-detects log format by analyzing file content.
 * Uses heuristics based on common patterns in different log formats.
 */
public class LogFormatDetector {

    private static final Logger logger = LoggerFactory.getLogger(LogFormatDetector.class);

    private static final int SAMPLE_LINES = 50; // Number of lines to analyze

    // Format detection patterns
    private static final Pattern JSON_PATTERN = Pattern.compile("^\\s*\\{.*\\}\\s*$");
    private static final Pattern XML_PATTERN = Pattern.compile("^\\s*<[^>]+>.*</[^>]+>\\s*$");
    private static final Pattern SYSLOG_RFC5424 = Pattern.compile("^<\\d+>\\d+ \\d{4}-\\d{2}-\\d{2}T");
    private static final Pattern SYSLOG_RFC3164 = Pattern.compile("^<\\d+>\\w{3}\\s+\\d+ \\d{2}:\\d{2}:\\d{2}");
    private static final Pattern LOG4J_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}");
    private static final Pattern APACHE_ACCESS = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+ - - \\[");
    private static final Pattern NGINX_ACCESS = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+ - .* \\[\\d{2}/\\w{3}/\\d{4}");
    private static final Pattern KUBERNETES_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z");

    /**
     * Detect log format from file by analyzing sample lines.
     */
    public static LogFormat detectFormat(Path file) {
        try {
            List<String> sampleLines = readSampleLines(file);
            return analyzeLines(sampleLines);
        } catch (IOException e) {
            logger.error("Failed to read file for format detection: {}", file, e);
            return LogFormat.UNKNOWN;
        }
    }

    private static List<String> readSampleLines(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < SAMPLE_LINES) {
                if (!line.isBlank()) {
                    lines.add(line);
                    count++;
                }
            }
        }
        return lines;
    }

    private static LogFormat analyzeLines(List<String> lines) {
        if (lines.isEmpty()) {
            return LogFormat.UNKNOWN;
        }

        int jsonCount = 0;
        int xmlCount = 0;
        int syslog5424Count = 0;
        int syslog3164Count = 0;
        int log4jCount = 0;
        int apacheCount = 0;
        int nginxCount = 0;
        int k8sCount = 0;

        for (String line : lines) {
            if (JSON_PATTERN.matcher(line).find()) {
                jsonCount++;
            }
            if (XML_PATTERN.matcher(line).find()) {
                xmlCount++;
            }
            if (SYSLOG_RFC5424.matcher(line).find()) {
                syslog5424Count++;
            }
            if (SYSLOG_RFC3164.matcher(line).find()) {
                syslog3164Count++;
            }
            if (LOG4J_PATTERN.matcher(line).find()) {
                log4jCount++;
            }
            if (APACHE_ACCESS.matcher(line).find()) {
                apacheCount++;
            }
            if (NGINX_ACCESS.matcher(line).find()) {
                nginxCount++;
            }
            if (KUBERNETES_PATTERN.matcher(line).find()) {
                k8sCount++;
            }
        }

        int totalLines = lines.size();
        double threshold = 0.6; // 60% of lines should match pattern

        // Check each format
        if ((double) jsonCount / totalLines >= threshold) {
            return detectJsonSubformat(lines.get(0));
        }
        if ((double) xmlCount / totalLines >= threshold) {
            return LogFormat.XML;
        }
        if ((double) syslog5424Count / totalLines >= threshold) {
            return LogFormat.SYSLOG;
        }
        if ((double) syslog3164Count / totalLines >= threshold) {
            return LogFormat.SYSLOG_RFC3164;
        }
        if ((double) k8sCount / totalLines >= threshold) {
            return LogFormat.KUBERNETES_POD;
        }
        if ((double) apacheCount / totalLines >= threshold) {
            return LogFormat.APACHE_ACCESS;
        }
        if ((double) nginxCount / totalLines >= threshold) {
            return LogFormat.NGINX_ACCESS;
        }
        if ((double) log4jCount / totalLines >= threshold) {
            return detectJavaLogFormat(lines.get(0));
        }

        // Default to generic format
        return LogFormat.UNKNOWN;
    }

    private static LogFormat detectJsonSubformat(String jsonLine) {
        // Check for specific JSON log formats
        if (jsonLine.contains("@timestamp")) {
            return LogFormat.LOGSTASH_JSON;
        }
        if (jsonLine.contains("eventTime") && jsonLine.contains("eventSource")) {
            return LogFormat.AWS_CLOUDWATCH;
        }
        if (jsonLine.contains("container_name") || jsonLine.contains("docker")) {
            return LogFormat.DOCKER;
        }
        if (jsonLine.contains("mongodb") || jsonLine.contains("component")) {
            return LogFormat.MONGODB;
        }
        return LogFormat.JSON;
    }

    private static LogFormat detectJavaLogFormat(String line) {
        if (line.contains("Log4j") || line.contains("log4j")) {
            return LogFormat.LOG4J;
        }
        if (line.contains("Logback") || line.contains("logback")) {
            return LogFormat.LOGBACK;
        }
        if (line.contains("SpringBoot") || line.contains("Spring")) {
            return LogFormat.SPRING_BOOT;
        }
        // Default to Log4j pattern
        return LogFormat.LOG4J;
    }
}
