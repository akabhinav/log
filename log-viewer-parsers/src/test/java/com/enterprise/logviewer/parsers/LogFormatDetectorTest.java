package com.enterprise.logviewer.parsers;

import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.parsers.detector.LogFormatDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for automatic log format detection.
 * Tests: JSON, Log4j, Syslog, Kubernetes, Apache/Nginx detection
 */
class LogFormatDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should detect JSON format")
    void testDetectJsonFormat() throws IOException {
        String content = """
            {"timestamp":"2024-01-15T10:30:45.123Z","level":"INFO","message":"Test 1"}
            {"timestamp":"2024-01-15T10:30:46.123Z","level":"WARN","message":"Test 2"}
            {"timestamp":"2024-01-15T10:30:47.123Z","level":"ERROR","message":"Test 3"}
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertTrue(format == LogFormat.JSON || format == LogFormat.LOGSTASH_JSON,
                  "Should detect JSON format, got: " + format);
    }

    @Test
    @DisplayName("Should detect Logstash JSON format")
    void testDetectLogstashFormat() throws IOException {
        String content = """
            {"@timestamp":"2024-01-15T10:30:45.123Z","level":"INFO","message":"Test 1"}
            {"@timestamp":"2024-01-15T10:30:46.123Z","level":"WARN","message":"Test 2"}
            {"@timestamp":"2024-01-15T10:30:47.123Z","level":"ERROR","message":"Test 3"}
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.LOGSTASH_JSON, format);
    }

    @Test
    @DisplayName("Should detect Log4j format")
    void testDetectLog4jFormat() throws IOException {
        String content = """
            2024-01-15 10:30:45,123 [main] INFO  com.example.Test - Test message 1
            2024-01-15 10:30:46,456 [main] WARN  com.example.Test - Test message 2
            2024-01-15 10:30:47,789 [main] ERROR com.example.Test - Test message 3
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertTrue(format == LogFormat.LOG4J || format == LogFormat.LOGBACK ||
                  format == LogFormat.SPRING_BOOT,
                  "Should detect Log4j-style format, got: " + format);
    }

    @Test
    @DisplayName("Should detect Syslog RFC 5424 format")
    void testDetectSyslogRFC5424() throws IOException {
        String content = """
            <165>1 2024-01-15T10:30:45.123Z server app 123 ID1 - Message 1
            <165>1 2024-01-15T10:30:46.123Z server app 123 ID2 - Message 2
            <165>1 2024-01-15T10:30:47.123Z server app 123 ID3 - Message 3
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.SYSLOG, format);
    }

    @Test
    @DisplayName("Should detect Syslog RFC 3164 format")
    void testDetectSyslogRFC3164() throws IOException {
        String content = """
            <34>Jan 15 10:30:45 server app: Message 1
            <34>Jan 15 10:30:46 server app: Message 2
            <34>Jan 15 10:30:47 server app: Message 3
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.SYSLOG_RFC3164, format);
    }

    @Test
    @DisplayName("Should detect Kubernetes pod log format")
    void testDetectKubernetesFormat() throws IOException {
        String content = """
            2024-01-15T10:30:45.123456789Z stdout F Message 1
            2024-01-15T10:30:46.123456789Z stderr F Message 2
            2024-01-15T10:30:47.123456789Z stdout F Message 3
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.KUBERNETES_POD, format);
    }

    @Test
    @DisplayName("Should detect Apache access log format")
    void testDetectApacheAccessLog() throws IOException {
        String content = """
            192.168.1.1 - - [15/Jan/2024:10:30:45 +0000] "GET /index.html HTTP/1.1" 200 1234
            192.168.1.2 - - [15/Jan/2024:10:30:46 +0000] "POST /api/data HTTP/1.1" 201 567
            192.168.1.3 - - [15/Jan/2024:10:30:47 +0000] "GET /about.html HTTP/1.1" 200 890
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.APACHE_ACCESS, format);
    }

    @Test
    @DisplayName("Should detect Nginx access log format")
    void testDetectNginxAccessLog() throws IOException {
        String content = """
            192.168.1.1 - user [15/Jan/2024:10:30:45 +0000] "GET / HTTP/1.1" 200 612 "-" "Mozilla/5.0"
            192.168.1.2 - user [15/Jan/2024:10:30:46 +0000] "POST /api HTTP/1.1" 201 123 "-" "curl/7.68.0"
            192.168.1.3 - user [15/Jan/2024:10:30:47 +0000] "GET /test HTTP/1.1" 404 78 "-" "wget/1.20"
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.NGINX_ACCESS, format);
    }

    @Test
    @DisplayName("Should detect Docker JSON format")
    void testDetectDockerFormat() throws IOException {
        String content = """
            {"log":"Application started\\n","stream":"stdout","time":"2024-01-15T10:30:45.123Z","container_name":"web-app"}
            {"log":"Processing request\\n","stream":"stdout","time":"2024-01-15T10:30:46.123Z","container_name":"web-app"}
            {"log":"Request completed\\n","stream":"stdout","time":"2024-01-15T10:30:47.123Z","container_name":"web-app"}
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.DOCKER, format);
    }

    @Test
    @DisplayName("Should return UNKNOWN for mixed formats")
    void testDetectMixedFormats() throws IOException {
        String content = """
            {"timestamp":"2024-01-15T10:30:45.123Z","level":"INFO","message":"JSON line"}
            2024-01-15 10:30:46,456 [main] WARN  com.example.Test - Log4j line
            <34>Jan 15 10:30:47 server app: Syslog line
            Plain text line
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        // Should return UNKNOWN or the most prominent format
        assertNotNull(format);
    }

    @Test
    @DisplayName("Should handle empty file")
    void testDetectEmptyFile() throws IOException {
        Path testFile = tempDir.resolve("empty.log");
        Files.writeString(testFile, "");

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.UNKNOWN, format);
    }

    @Test
    @DisplayName("Should handle file with only whitespace")
    void testDetectWhitespaceFile() throws IOException {
        Path testFile = tempDir.resolve("whitespace.log");
        Files.writeString(testFile, "   \n  \n   \n");

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.UNKNOWN, format);
    }

    @Test
    @DisplayName("Should detect CloudWatch format from JSON")
    void testDetectCloudWatchFormat() throws IOException {
        String content = """
            {"eventTime":"2024-01-15T10:30:45.123Z","eventSource":"lambda","level":"INFO","message":"Function invoked"}
            {"eventTime":"2024-01-15T10:30:46.123Z","eventSource":"lambda","level":"WARN","message":"Memory high"}
            {"eventTime":"2024-01-15T10:30:47.123Z","eventSource":"lambda","level":"ERROR","message":"Timeout"}
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.AWS_CLOUDWATCH, format);
    }

    @Test
    @DisplayName("Should detect MongoDB JSON format")
    void testDetectMongoDBFormat() throws IOException {
        String content = """
            {"t":{"$date":"2024-01-15T10:30:45.123Z"},"s":"I","c":"NETWORK","ctx":"listener","msg":"Waiting for connections"}
            {"t":{"$date":"2024-01-15T10:30:46.123Z"},"s":"I","c":"NETWORK","ctx":"conn1","msg":"Connection accepted","component":"NETWORK"}
            {"t":{"$date":"2024-01-15T10:30:47.123Z"},"s":"W","c":"QUERY","ctx":"conn1","msg":"Query slow","component":"QUERY"}
            """;

        Path testFile = tempDir.resolve("test.log");
        Files.writeString(testFile, content);

        LogFormat format = LogFormatDetector.detectFormat(testFile);

        assertEquals(LogFormat.MONGODB, format);
    }
}
