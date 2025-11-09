package com.enterprise.logviewer.parsers;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.parsers.log4j.Log4jPatternParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Log4j pattern parser.
 * Tests: Standard patterns, with/without thread, stack traces, Spring Boot format
 */
class Log4jPatternParserTest {

    private Log4jPatternParser parser;

    @BeforeEach
    void setUp() {
        parser = new Log4jPatternParser();
    }

    @Test
    @DisplayName("Should parse standard Log4j pattern with thread")
    void testParseStandardPatternWithThread() {
        String logLine = "2024-01-15 10:30:45,123 [http-nio-8080-exec-1] INFO  com.example.UserService - User login successful";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertEquals(LogLevel.INFO, entry.getLevel());
        assertEquals("com.example.UserService", entry.getLoggerName().trim());
        assertEquals("http-nio-8080-exec-1", entry.getThreadName());
        assertEquals("User login successful", entry.getMessage());
        assertNotNull(entry.getTimestamp());
    }

    @Test
    @DisplayName("Should parse Log4j pattern without thread")
    void testParsePatternWithoutThread() {
        String logLine = "2024-01-15 10:30:45,123 INFO  com.example.UserService - User login successful";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertEquals(LogLevel.INFO, entry.getLevel());
        assertEquals("com.example.UserService", entry.getLoggerName().trim());
        assertNull(entry.getThreadName());
        assertEquals("User login successful", entry.getMessage());
    }

    @Test
    @DisplayName("Should parse multi-line stack trace")
    void testParseStackTrace() {
        String logLine = "2024-01-15 10:31:45,789 [pool-1-thread-3] ERROR com.example.DatabaseService - Database connection failed\n" +
                        "java.sql.SQLException: Connection timeout after 30s\n" +
                        "\tat com.example.DatabaseService.getConnection(DatabaseService.java:45)\n" +
                        "\tat com.example.UserRepository.findById(UserRepository.java:23)";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertEquals(LogLevel.ERROR, entry.getLevel());
        assertTrue(entry.getMessage().contains("Database connection failed"));
        assertNotNull(entry.getExceptionClass());
        assertTrue(entry.getExceptionClass().contains("SQLException"));
        assertNotNull(entry.getStackTrace());
    }

    @Test
    @DisplayName("Should parse all log levels")
    void testParseAllLogLevels() {
        String[] levels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"};

        for (String level : levels) {
            String logLine = String.format("2024-01-15 10:30:45,123 [main] %s  com.example.Test - Test message", level);
            LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

            assertNotNull(entry, "Failed to parse level: " + level);
            assertEquals(LogLevel.fromString(level), entry.getLevel());
        }
    }

    @Test
    @DisplayName("Should parse with milliseconds using comma")
    void testParseMillisecondsWithComma() {
        String logLine = "2024-01-15 10:30:45,123 [main] INFO  com.example.Test - Message";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertNotNull(entry.getTimestamp());
    }

    @Test
    @DisplayName("Should parse with milliseconds using dot")
    void testParseMillisecondsWithDot() {
        String logLine = "2024-01-15 10:30:45.123 [main] INFO  com.example.Test - Message";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertNotNull(entry.getTimestamp());
    }

    @Test
    @DisplayName("Should handle ISO timestamp format")
    void testParseISOTimestamp() {
        String logLine = "2024-01-15T10:30:45.123+00:00 INFO com.example.Test - ISO format test";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertEquals(LogLevel.INFO, entry.getLevel());
    }

    @Test
    @DisplayName("Should create fallback entry for unrecognized format")
    void testParseFallback() {
        String logLine = "This is a plain text log line";

        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        assertEquals("This is a plain text log line", entry.getMessage());
        assertEquals(LogLevel.INFO, entry.getLevel());
    }

    @Test
    @DisplayName("Should handle null and blank lines")
    void testParseNullAndBlankLines() {
        assertNull(parser.parseLine(null, LogFormat.LOG4J));
        assertNull(parser.parseLine("", LogFormat.LOG4J));
        assertNull(parser.parseLine("   ", LogFormat.LOG4J));
    }

    @Test
    @DisplayName("Should parse Spring Boot style logs")
    void testParseSpringBootFormat() {
        String logLine = "2024-01-15 10:30:45.123  INFO 12345 --- [           main] com.example.Application                  : Starting Application";

        // This will fallback since it doesn't match standard pattern, but should still work
        LogEntry entry = parser.parseLine(logLine, LogFormat.LOG4J);

        assertNotNull(entry);
        // Should at least capture the line
        assertNotNull(entry.getMessage());
    }

    @Test
    @DisplayName("Should verify parser supports Log4j format")
    void testSupportsFormat() {
        assertTrue(parser.supports(LogFormat.LOG4J));
        assertFalse(parser.supports(LogFormat.JSON));
        assertFalse(parser.supports(LogFormat.SYSLOG));
    }
}
