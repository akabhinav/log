package com.enterprise.logviewer.parsers.generic;

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
 * Generic parser using configurable regex patterns for custom log formats.
 * Supports named capture groups: timestamp, level, logger, thread, message, etc.
 */
public class RegexLogParser extends AbstractLogParser {

    private final Pattern pattern;
    private final Map<String, DateTimeFormatter> dateFormatters;
    private final LogFormat format;

    /**
     * Create a regex parser with custom pattern.
     * @param regex Regex pattern with named groups: (?<timestamp>...), (?<level>...), (?<message>...), etc.
     * @param timestampFormat Optional datetime format string
     */
    public RegexLogParser(String regex, String timestampFormat) {
        this(regex, timestampFormat, LogFormat.CUSTOM_REGEX);
    }

    public RegexLogParser(String regex, String timestampFormat, LogFormat format) {
        this.pattern = Pattern.compile(regex);
        this.dateFormatters = new HashMap<>();
        if (timestampFormat != null && !timestampFormat.isBlank()) {
            dateFormatters.put("default", DateTimeFormatter.ofPattern(timestampFormat));
        }
        this.format = format;
    }

    @Override
    protected LogFormat getSupportedFormat() {
        return format;
    }

    @Override
    public LogEntry parseLine(String line, LogFormat format) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            return createFallbackEntry(line);
        }

        try {
            LogEntry.Builder builder = LogEntry.builder()
                .rawLine(line)
                .format(this.format);

            // Extract timestamp
            String timestampStr = extractGroup(matcher, "timestamp", "time", "ts", "datetime");
            if (timestampStr != null) {
                Instant timestamp = parseTimestamp(timestampStr);
                builder.timestamp(timestamp);
            }

            // Extract level
            String levelStr = extractGroup(matcher, "level", "severity", "loglevel");
            if (levelStr != null) {
                builder.level(LogLevel.fromString(levelStr));
            } else {
                builder.level(LogLevel.INFO);
            }

            // Extract message (required)
            String message = extractGroup(matcher, "message", "msg", "text");
            if (message != null) {
                builder.message(message);
            } else {
                builder.message(line); // Fallback to entire line
            }

            // Extract optional fields
            extractAndSet(matcher, builder, "logger", builder::loggerName);
            extractAndSet(matcher, builder, "thread", builder::threadName);
            extractAndSet(matcher, builder, "host", builder::sourceServer);
            extractAndSet(matcher, builder, "file", builder::sourceFile);

            String lineNum = extractGroup(matcher, "line", "lineNumber");
            if (lineNum != null) {
                try {
                    builder.lineNumber(Integer.parseInt(lineNum));
                } catch (NumberFormatException ignored) {}
            }

            return builder.build();

        } catch (Exception e) {
            logger.warn("Failed to parse line with regex: {}", line, e);
            return createFallbackEntry(line);
        }
    }

    private String extractGroup(Matcher matcher, String... groupNames) {
        for (String name : groupNames) {
            try {
                String value = matcher.group(name);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            } catch (IllegalArgumentException ignored) {
                // Group doesn't exist
            }
        }
        return null;
    }

    private void extractAndSet(Matcher matcher, LogEntry.Builder builder,
                               String groupName, java.util.function.Consumer<String> setter) {
        String value = extractGroup(matcher, groupName);
        if (value != null) {
            setter.accept(value);
        }
    }

    private Instant parseTimestamp(String timestampStr) {
        // Try ISO-8601 first
        try {
            return Instant.parse(timestampStr);
        } catch (Exception ignored) {}

        // Try configured formatters
        for (DateTimeFormatter formatter : dateFormatters.values()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(timestampStr, formatter);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {}
        }

        // Try epoch millis
        try {
            long epochMillis = Long.parseLong(timestampStr);
            return Instant.ofEpochMilli(epochMillis);
        } catch (Exception ignored) {}

        logger.warn("Failed to parse timestamp: {}", timestampStr);
        return Instant.now();
    }

    private LogEntry createFallbackEntry(String line) {
        return LogEntry.builder()
            .message(line)
            .rawLine(line)
            .format(format)
            .level(LogLevel.INFO)
            .build();
    }
}
