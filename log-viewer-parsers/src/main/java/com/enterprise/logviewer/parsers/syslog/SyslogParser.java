package com.enterprise.logviewer.parsers.syslog;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.parsers.AbstractLogParser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Syslog format logs (RFC 5424 and RFC 3164).
 * Examples:
 * RFC 3164: <34>Oct 11 22:14:15 mymachine su: 'su root' failed for user on /dev/pts/8
 * RFC 5424: <165>1 2003-10-11T22:14:15.003Z mymachine.example.com evntslog - ID47 [exampleSDID@32473 iut="3"] BOMAn application event log entry...
 */
public class SyslogParser extends AbstractLogParser {

    // RFC 3164 pattern
    private static final Pattern RFC3164_PATTERN = Pattern.compile(
        "^<(\\d+)>" +                                       // priority
        "(\\w+\\s+\\d+\\s+\\d{2}:\\d{2}:\\d{2})" +         // timestamp
        "\\s+(\\S+)" +                                      // hostname
        "\\s+(\\S+):" +                                     // process/tag
        "\\s*(.+)$"                                         // message
    );

    // RFC 5424 pattern
    private static final Pattern RFC5424_PATTERN = Pattern.compile(
        "^<(\\d+)>" +                                       // priority
        "(\\d+)" +                                          // version
        "\\s+(\\S+)" +                                      // timestamp
        "\\s+(\\S+)" +                                      // hostname
        "\\s+(\\S+)" +                                      // app-name
        "\\s+(\\S+)" +                                      // proc-id
        "\\s+(\\S+)" +                                      // msg-id
        "\\s+(\\[.+?\\])?" +                                // structured-data
        "\\s*(.*)$"                                         // message
    );

    private static final DateTimeFormatter RFC3164_DATE = DateTimeFormatter.ofPattern("MMM dd HH:mm:ss");

    @Override
    protected LogFormat getSupportedFormat() {
        return LogFormat.SYSLOG;
    }

    @Override
    public LogEntry parseLine(String line, LogFormat format) {
        if (line == null || line.isBlank()) {
            return null;
        }

        // Try RFC 5424 first (more structured)
        Matcher matcher = RFC5424_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseRFC5424(line, matcher);
        }

        // Try RFC 3164
        matcher = RFC3164_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseRFC3164(line, matcher);
        }

        // Fallback
        return createFallbackEntry(line);
    }

    private LogEntry parseRFC5424(String line, Matcher matcher) {
        try {
            int priority = Integer.parseInt(matcher.group(1));
            String timestampStr = matcher.group(3);
            String hostname = matcher.group(4);
            String appName = matcher.group(5);
            String procId = matcher.group(6);
            String msgId = matcher.group(7);
            String structuredData = matcher.group(8);
            String message = matcher.group(9);

            Instant timestamp = Instant.parse(timestampStr);
            LogLevel level = priorityToLevel(priority);

            LogEntry.Builder builder = LogEntry.builder()
                .timestamp(timestamp)
                .level(level)
                .message(message)
                .sourceServer(hostname)
                .loggerName(appName)
                .threadName(procId)
                .rawLine(line)
                .format(LogFormat.SYSLOG);

            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("priority", priority);
            metadata.put("msgId", msgId);
            if (structuredData != null && !structuredData.equals("-")) {
                metadata.put("structuredData", structuredData);
            }
            builder.metadata(metadata);

            return builder.build();

        } catch (Exception e) {
            logger.warn("Failed to parse RFC 5424 syslog: {}", line, e);
            return createFallbackEntry(line);
        }
    }

    private LogEntry parseRFC3164(String line, Matcher matcher) {
        try {
            int priority = Integer.parseInt(matcher.group(1));
            String timestampStr = matcher.group(2);
            String hostname = matcher.group(3);
            String process = matcher.group(4);
            String message = matcher.group(5);

            // RFC 3164 doesn't include year, so use current year
            LocalDateTime ldt = LocalDateTime.parse(
                java.time.Year.now().getValue() + " " + timestampStr,
                DateTimeFormatter.ofPattern("yyyy MMM dd HH:mm:ss")
            );
            Instant timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant();

            LogLevel level = priorityToLevel(priority);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("priority", priority);

            return LogEntry.builder()
                .timestamp(timestamp)
                .level(level)
                .message(message)
                .sourceServer(hostname)
                .loggerName(process)
                .rawLine(line)
                .format(LogFormat.SYSLOG_RFC3164)
                .metadata(metadata)
                .build();

        } catch (Exception e) {
            logger.warn("Failed to parse RFC 3164 syslog: {}", line, e);
            return createFallbackEntry(line);
        }
    }

    /**
     * Convert syslog priority to log level.
     * Priority = Facility * 8 + Severity
     * Severity: 0=Emergency, 1=Alert, 2=Critical, 3=Error, 4=Warning, 5=Notice, 6=Info, 7=Debug
     */
    private LogLevel priorityToLevel(int priority) {
        int severity = priority % 8;
        return switch (severity) {
            case 0, 1, 2 -> LogLevel.FATAL;
            case 3 -> LogLevel.ERROR;
            case 4 -> LogLevel.WARN;
            case 5, 6 -> LogLevel.INFO;
            case 7 -> LogLevel.DEBUG;
            default -> LogLevel.INFO;
        };
    }

    private LogEntry createFallbackEntry(String line) {
        return LogEntry.builder()
            .message(line)
            .rawLine(line)
            .format(LogFormat.SYSLOG)
            .level(LogLevel.INFO)
            .build();
    }
}
