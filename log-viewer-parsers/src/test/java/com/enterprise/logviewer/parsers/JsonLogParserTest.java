package com.enterprise.logviewer.parsers;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.parsers.json.JsonLogParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for JSON log parser.
 * Tests: JSON format parsing, field extraction, exception handling, metadata
 */
class JsonLogParserTest {

    private JsonLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonLogParser();
    }

    @Test
    @DisplayName("Should parse standard JSON log with all fields")
    void testParseStandardJsonLog() {
        String logLine = "{\"timestamp\":\"2024-01-15T10:30:45.123Z\",\"level\":\"INFO\"," +
                        "\"logger\":\"com.example.UserService\",\"thread\":\"http-nio-8080-exec-1\"," +
                        "\"message\":\"User login successful\",\"userId\":12345}";

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        assertNotNull(entry, "Entry should not be null");
        assertEquals(LogLevel.INFO, entry.getLevel());
        assertEquals("com.example.UserService", entry.getLoggerName());
        assertEquals("http-nio-8080-exec-1", entry.getThreadName());
        assertEquals("User login successful", entry.getMessage());
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getMetadata().containsKey("userId"));
    }

    @Test
    @DisplayName("Should parse JSON log with exception")
    void testParseJsonWithException() {
        String logLine = "{\"timestamp\":\"2024-01-15T10:31:45.789Z\",\"level\":\"ERROR\"," +
                        "\"logger\":\"com.example.DatabaseService\"," +
                        "\"message\":\"Database connection timeout\"," +
                        "\"exception\":\"java.sql.SQLException\"," +
                        "\"stackTrace\":\"java.sql.SQLException: Connection timeout\\n\\tat com.example.DatabaseService.getConnection(DatabaseService.java:45)\"}";

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        assertNotNull(entry);
        assertEquals(LogLevel.ERROR, entry.getLevel());
        assertEquals("Database connection timeout", entry.getMessage());
        assertNotNull(entry.getStackTrace());
        assertTrue(entry.getStackTrace().contains("java.sql.SQLException"));
    }

    @Test
    @DisplayName("Should handle Logstash JSON format")
    void testParseLogstashFormat() {
        String logLine = "{\"@timestamp\":\"2024-01-15T10:30:45.123Z\"," +
                        "\"level\":\"WARN\"," +
                        "\"message\":\"High memory usage detected\"}";

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        assertNotNull(entry);
        assertEquals(LogLevel.WARN, entry.getLevel());
        assertEquals("High memory usage detected", entry.getMessage());
        assertNotNull(entry.getTimestamp());
    }

    @Test
    @DisplayName("Should parse CloudWatch JSON format")
    void testParseCloudWatchFormat() {
        String logLine = "{\"time\":\"2024-01-15T10:30:45.123Z\"," +
                        "\"severity\":\"ERROR\"," +
                        "\"msg\":\"Lambda function timeout\"}";

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        assertNotNull(entry);
        assertEquals(LogLevel.ERROR, entry.getLevel());
        assertEquals("Lambda function timeout", entry.getMessage());
    }

    @Test
    @DisplayName("Should handle epoch milliseconds timestamp")
    void testParseEpochTimestamp() {
        String logLine = "{\"timestamp\":1705315845123," +
                        "\"level\":\"INFO\"," +
                        "\"message\":\"Event processed\"}";

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        assertNotNull(entry);
        assertNotNull(entry.getTimestamp());
        assertEquals("Event processed", entry.getMessage());
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void testParseMalformedJson() {
        String logLine = "{\"timestamp\":\"2024-01-15T10:30:45.123Z\",\"level\":\"INFO\""; // Missing closing brace

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        // Should create fallback entry
        assertNotNull(entry);
        assertEquals(LogLevel.UNKNOWN, entry.getLevel());
        assertTrue(entry.getMessage().contains("timestamp"));
    }

    @Test
    @DisplayName("Should extract metadata from JSON fields")
    void testExtractMetadata() {
        String logLine = "{\"timestamp\":\"2024-01-15T10:30:45.123Z\"," +
                        "\"level\":\"INFO\"," +
                        "\"message\":\"Order processed\"," +
                        "\"orderId\":\"ORD-001\"," +
                        "\"customerId\":12345," +
                        "\"amount\":299.99}";

        LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

        assertNotNull(entry);
        assertEquals(3, entry.getMetadata().size());
        assertTrue(entry.getMetadata().containsKey("orderId"));
        assertTrue(entry.getMetadata().containsKey("customerId"));
        assertTrue(entry.getMetadata().containsKey("amount"));
    }

    @Test
    @DisplayName("Should parse different log level variations")
    void testParseVariousLogLevels() {
        String[] levels = {"DEBUG", "INFO", "WARN", "WARNING", "ERROR", "ERR", "FATAL", "CRITICAL"};
        LogLevel[] expectedLevels = {LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.WARN,
                                    LogLevel.ERROR, LogLevel.ERROR, LogLevel.FATAL, LogLevel.FATAL};

        for (int i = 0; i < levels.length; i++) {
            String logLine = String.format("{\"level\":\"%s\",\"message\":\"Test message\"}", levels[i]);
            LogEntry entry = parser.parseLine(logLine, LogFormat.JSON);

            assertNotNull(entry);
            assertEquals(expectedLevels[i], entry.getLevel(),
                        "Failed for level: " + levels[i]);
        }
    }

    @Test
    @DisplayName("Should handle null and blank lines")
    void testParseNullAndBlankLines() {
        assertNull(parser.parseLine(null, LogFormat.JSON));
        assertNull(parser.parseLine("", LogFormat.JSON));
        assertNull(parser.parseLine("   ", LogFormat.JSON));
    }

    @Test
    @DisplayName("Should verify parser supports JSON format")
    void testSupportsFormat() {
        assertTrue(parser.supports(LogFormat.JSON));
        assertFalse(parser.supports(LogFormat.LOG4J));
        assertFalse(parser.supports(LogFormat.SYSLOG));
    }
}
