package com.enterprise.logviewer.parsers.log4j;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.parsers.AbstractLogParser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Log4j/Log4j2/Logback pattern-based logs.
 * Supports common patterns like:
 * - %d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n
 * - %d [%thread] %-5level %c{1} - %msg%n
 */
public class Log4jPatternParser extends AbstractLogParser {

    // Common Log4j pattern: timestamp [thread] level logger - message
    private static final Pattern LOG4J_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})" + // timestamp
        "\\s*\\[([^\\]]+)\\]" +                                         // thread
        "\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)" +                    // level
        "\\s+([^-]+)" +                                                 // logger
        "\\s*-\\s*(.+)$",                                               // message
        Pattern.DOTALL
    );

    // Alternative pattern without thread
    private static final Pattern LOG4J_SIMPLE_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})" + // timestamp
        "\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)" +                    // level
        "\\s+([^-]+)" +                                                 // logger
        "\\s*-\\s*(.+)$",                                               // message
        Pattern.DOTALL
    );

    // ISO timestamp pattern
    private static final Pattern ISO_TIMESTAMP_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}[+-]\\d{2}:\\d{2})" +
        "\\s+(.+)$"
    );

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    @Override
    protected LogFormat getSupportedFormat() {
        return LogFormat.LOG4J;
    }

    @Override
    public LogEntry parseLine(String line, LogFormat format) {
        if (line == null || line.isBlank()) {
            return null;
        }

        // Try main pattern
        Matcher matcher = LOG4J_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseWithMatcher(line, matcher, true);
        }

        // Try simple pattern
        matcher = LOG4J_SIMPLE_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseWithMatcher(line, matcher, false);
        }

        // Try ISO timestamp pattern
        matcher = ISO_TIMESTAMP_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseIsoFormat(line, matcher);
        }

        // Fallback: create basic entry
        return createFallbackEntry(line);
    }

    private LogEntry parseWithMatcher(String line, Matcher matcher, boolean hasThread) {
        try {
            String timestampStr = matcher.group(1);
            Instant timestamp = parseTimestamp(timestampStr);

            int groupOffset = hasThread ? 1 : 0;
            String thread = hasThread ? matcher.group(2).trim() : null;
            String levelStr = matcher.group(2 + groupOffset).trim();
            String logger = matcher.group(3 + groupOffset).trim();
            String message = matcher.group(4 + groupOffset);

            LogEntry.Builder builder = LogEntry.builder()
                .timestamp(timestamp)
                .level(LogLevel.fromString(levelStr))
                .loggerName(logger)
                .message(message)
                .rawLine(line)
                .format(LogFormat.LOG4J);

            if (thread != null) {
                builder.threadName(thread);
            }

            // Check for exception in message
            if (message.contains("Exception") || message.contains("Error")) {
                ExceptionInfo exInfo = parseException(message);
                if (exInfo != null) {
                    builder.exceptionClass(exInfo.exceptionClass())
                           .stackTrace(exInfo.stackTrace());
                }
            }

            return builder.build();

        } catch (Exception e) {
            logger.warn("Failed to parse log4j line: {}", line, e);
            return createFallbackEntry(line);
        }
    }

    private LogEntry parseIsoFormat(String line, Matcher matcher) {
        try {
            String timestampStr = matcher.group(1);
            String rest = matcher.group(2);

            Instant timestamp = Instant.parse(timestampStr.replace(',', '.'));

            return LogEntry.builder()
                .timestamp(timestamp)
                .message(rest)
                .rawLine(line)
                .format(LogFormat.LOG4J)
                .level(LogLevel.INFO)
                .build();

        } catch (Exception e) {
            return createFallbackEntry(line);
        }
    }

    private Instant parseTimestamp(String timestampStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(timestampStr.replace(',', '.'), formatter);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        // Fallback to current time
        logger.warn("Failed to parse timestamp: {}", timestampStr);
        return Instant.now();
    }

    private LogEntry createFallbackEntry(String line) {
        return LogEntry.builder()
            .message(line)
            .rawLine(line)
            .format(LogFormat.LOG4J)
            .level(LogLevel.INFO)
            .build();
    }
}
