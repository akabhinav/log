package com.enterprise.logviewer.parsers.json;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.parsers.AbstractLogParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for JSON-formatted logs.
 * Supports: Logstash JSON, CloudWatch, Docker, MongoDB, Elasticsearch, custom JSON logs.
 */
public class JsonLogParser extends AbstractLogParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Common timestamp field names
    private static final String[] TIMESTAMP_FIELDS = {
        "timestamp", "time", "@timestamp", "datetime", "date", "ts", "eventTime"
    };

    // Common level field names
    private static final String[] LEVEL_FIELDS = {
        "level", "severity", "loglevel", "priority", "lvl"
    };

    // Common message field names
    private static final String[] MESSAGE_FIELDS = {
        "message", "msg", "text", "log", "content"
    };

    @Override
    protected LogFormat getSupportedFormat() {
        return LogFormat.JSON;
    }

    @Override
    public LogEntry parseLine(String line, LogFormat format) {
        if (line == null || line.isBlank()) {
            return null;
        }

        try {
            JsonNode json = objectMapper.readTree(line);

            LogEntry.Builder builder = LogEntry.builder()
                .format(LogFormat.JSON)
                .rawLine(line);

            // Extract timestamp
            Instant timestamp = extractTimestamp(json);
            if (timestamp != null) {
                builder.timestamp(timestamp);
            }

            // Extract log level
            LogLevel level = extractLevel(json);
            builder.level(level);

            // Extract message
            String message = extractMessage(json);
            if (message != null) {
                builder.message(message);
            }

            // Extract optional fields
            extractOptionalField(json, "logger", builder::loggerName);
            extractOptionalField(json, "thread", builder::threadName);
            extractOptionalField(json, "source", builder::sourceServer);
            extractOptionalField(json, "file", builder::sourceFile);

            if (json.has("line")) {
                builder.lineNumber(json.get("line").asInt());
            }

            // Exception handling
            if (json.has("exception") || json.has("error") || json.has("stackTrace")) {
                String stackTrace = extractStackTrace(json);
                if (stackTrace != null) {
                    builder.stackTrace(stackTrace);
                    ExceptionInfo exInfo = parseException(stackTrace);
                    if (exInfo != null) {
                        builder.exceptionClass(exInfo.exceptionClass());
                    }
                }
            }

            // Store all fields as metadata
            Map<String, Object> metadata = new HashMap<>();
            json.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isValueNode()) {
                    metadata.put(key, value.asText());
                }
            });
            builder.metadata(metadata);

            return builder.build();

        } catch (Exception e) {
            logger.warn("Failed to parse JSON log line: {}", line, e);
            return createFallbackEntry(line);
        }
    }

    private Instant extractTimestamp(JsonNode json) {
        for (String field : TIMESTAMP_FIELDS) {
            if (json.has(field)) {
                try {
                    String timeStr = json.get(field).asText();
                    // Try parsing as ISO-8601
                    return Instant.parse(timeStr);
                } catch (Exception e) {
                    // Try parsing as epoch millis
                    try {
                        long epochMillis = json.get(field).asLong();
                        return Instant.ofEpochMilli(epochMillis);
                    } catch (Exception ex) {
                        logger.debug("Failed to parse timestamp from field {}", field);
                    }
                }
            }
        }
        return null;
    }

    private LogLevel extractLevel(JsonNode json) {
        for (String field : LEVEL_FIELDS) {
            if (json.has(field)) {
                String levelStr = json.get(field).asText();
                return LogLevel.fromString(levelStr);
            }
        }
        return LogLevel.INFO;
    }

    private String extractMessage(JsonNode json) {
        for (String field : MESSAGE_FIELDS) {
            if (json.has(field)) {
                return json.get(field).asText();
            }
        }
        // If no message field found, use the entire JSON as message
        return json.toString();
    }

    private String extractStackTrace(JsonNode json) {
        if (json.has("stackTrace")) {
            return json.get("stackTrace").asText();
        }
        if (json.has("exception")) {
            JsonNode ex = json.get("exception");
            if (ex.isTextual()) {
                return ex.asText();
            }
            return ex.toString();
        }
        if (json.has("error")) {
            return json.get("error").asText();
        }
        return null;
    }

    private void extractOptionalField(JsonNode json, String field, java.util.function.Consumer<String> setter) {
        if (json.has(field)) {
            setter.accept(json.get(field).asText());
        }
    }

    private LogEntry createFallbackEntry(String line) {
        return LogEntry.builder()
            .message(line)
            .rawLine(line)
            .format(LogFormat.JSON)
            .level(LogLevel.UNKNOWN)
            .build();
    }
}
