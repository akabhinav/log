package com.enterprise.logviewer.parsers;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.parsers.syslog.SyslogParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Syslog parser.
 * Tests: RFC 3164 and RFC 5424 formats, priority to log level mapping
 */
class SyslogParserTest {

    private SyslogParser parser;

    @BeforeEach
    void setUp() {
        parser = new SyslogParser();
    }

    @Test
    @DisplayName("Should parse RFC 3164 format")
    void testParseRFC3164() {
        String logLine = "<34>Jan 15 10:30:45 webserver01 nginx: 192.168.1.50 - GET /api/users HTTP/1.1 200";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertEquals("webserver01", entry.getSourceServer());
        assertEquals("nginx", entry.getLoggerName());
        assertTrue(entry.getMessage().contains("192.168.1.50"));
        assertNotNull(entry.getTimestamp());
    }

    @Test
    @DisplayName("Should parse RFC 5424 format")
    void testParseRFC5424() {
        String logLine = "<165>1 2024-01-15T10:30:45.123Z webserver01 app 12345 ID47 [exampleSDID@32473 iut=\"3\"] Application event log";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertEquals("webserver01", entry.getSourceServer());
        assertEquals("app", entry.getLoggerName());
        assertEquals("12345", entry.getThreadName()); // proc-id mapped to thread
        assertEquals("Application event log", entry.getMessage());
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getMetadata().containsKey("msgId"));
    }

    @Test
    @DisplayName("Should map priority to log level correctly")
    void testPriorityToLogLevelMapping() {
        // Priority = Facility * 8 + Severity
        // Testing different severities (0-7)
        String[] logLines = {
            "<0>Jan 15 10:30:45 server app: Emergency",      // Severity 0 -> FATAL
            "<1>Jan 15 10:30:45 server app: Alert",          // Severity 1 -> FATAL
            "<2>Jan 15 10:30:45 server app: Critical",       // Severity 2 -> FATAL
            "<3>Jan 15 10:30:45 server app: Error",          // Severity 3 -> ERROR
            "<4>Jan 15 10:30:45 server app: Warning",        // Severity 4 -> WARN
            "<5>Jan 15 10:30:45 server app: Notice",         // Severity 5 -> INFO
            "<6>Jan 15 10:30:45 server app: Info",           // Severity 6 -> INFO
            "<7>Jan 15 10:30:45 server app: Debug"           // Severity 7 -> DEBUG
        };

        LogLevel[] expectedLevels = {
            LogLevel.FATAL, LogLevel.FATAL, LogLevel.FATAL,
            LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO,
            LogLevel.INFO, LogLevel.DEBUG
        };

        for (int i = 0; i < logLines.length; i++) {
            LogEntry entry = parser.parseLine(logLines[i], LogFormat.SYSLOG);
            assertNotNull(entry, "Failed to parse line " + i);
            assertEquals(expectedLevels[i], entry.getLevel(),
                        "Priority mapping failed for severity " + i);
        }
    }

    @Test
    @DisplayName("Should handle different facilities")
    void testDifferentFacilities() {
        // User facility (1) with ERROR severity (3) = 1*8+3 = 11
        String logLine = "<11>Jan 15 10:30:45 server app: User error message";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertEquals(LogLevel.ERROR, entry.getLevel());

        // Mail facility (2) with INFO severity (6) = 2*8+6 = 22
        logLine = "<22>Jan 15 10:30:45 mailserver postfix: Mail sent successfully";

        entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertEquals(LogLevel.INFO, entry.getLevel());
    }

    @Test
    @DisplayName("Should handle RFC 5424 with structured data")
    void testRFC5424WithStructuredData() {
        String logLine = "<165>1 2024-01-15T10:30:45.123Z server app 123 MSG01 " +
                        "[exampleSDID@32473 iut=\"3\" eventSource=\"Application\"] Test message";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertTrue(entry.getMetadata().containsKey("structuredData"));
        String structuredData = (String) entry.getMetadata().get("structuredData");
        assertTrue(structuredData.contains("exampleSDID"));
    }

    @Test
    @DisplayName("Should handle RFC 5424 without structured data")
    void testRFC5424WithoutStructuredData() {
        String logLine = "<165>1 2024-01-15T10:30:45.123Z server app 123 MSG01 - Simple message";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertEquals("Simple message", entry.getMessage());
        assertFalse(entry.getMetadata().containsKey("structuredData") ||
                   "-".equals(entry.getMetadata().get("structuredData")));
    }

    @Test
    @DisplayName("Should handle various month abbreviations")
    void testMonthAbbreviations() {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        for (String month : months) {
            String logLine = String.format("<%d>%s 15 10:30:45 server app: Test", 34, month);
            LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

            assertNotNull(entry, "Failed to parse month: " + month);
            assertNotNull(entry.getTimestamp());
        }
    }

    @Test
    @DisplayName("Should create fallback entry for malformed syslog")
    void testParseMalformedSyslog() {
        String logLine = "This is not a valid syslog message";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertEquals("This is not a valid syslog message", entry.getMessage());
        assertEquals(LogLevel.INFO, entry.getLevel());
    }

    @Test
    @DisplayName("Should handle null and blank lines")
    void testParseNullAndBlankLines() {
        assertNull(parser.parseLine(null, LogFormat.SYSLOG));
        assertNull(parser.parseLine("", LogFormat.SYSLOG));
        assertNull(parser.parseLine("   ", LogFormat.SYSLOG));
    }

    @Test
    @DisplayName("Should verify parser supports Syslog format")
    void testSupportsFormat() {
        assertTrue(parser.supports(LogFormat.SYSLOG));
        assertFalse(parser.supports(LogFormat.JSON));
        assertFalse(parser.supports(LogFormat.LOG4J));
    }

    @Test
    @DisplayName("Should extract metadata for priority")
    void testExtractPriorityMetadata() {
        String logLine = "<165>1 2024-01-15T10:30:45.123Z server app 123 MSG - Test";

        LogEntry entry = parser.parseLine(logLine, LogFormat.SYSLOG);

        assertNotNull(entry);
        assertTrue(entry.getMetadata().containsKey("priority"));
        assertEquals(165, entry.getMetadata().get("priority"));
    }
}
