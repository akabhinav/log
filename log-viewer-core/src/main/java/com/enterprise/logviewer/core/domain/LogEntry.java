package com.enterprise.logviewer.core.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single log entry with universal fields supporting all log formats.
 * Immutable domain entity.
 */
public final class LogEntry {

    private final String id;
    private final Instant timestamp;
    private final LogLevel level;
    private final String message;
    private final String loggerName;
    private final String threadName;
    private final String sourceServer;
    private final String sourceFile;
    private final Integer lineNumber;
    private final String exceptionClass;
    private final String stackTrace;
    private final Map<String, Object> metadata;
    private final String rawLine;
    private final long fileOffset;
    private final LogFormat format;

    private LogEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp;
        this.level = builder.level;
        this.message = builder.message;
        this.loggerName = builder.loggerName;
        this.threadName = builder.threadName;
        this.sourceServer = builder.sourceServer;
        this.sourceFile = builder.sourceFile;
        this.lineNumber = builder.lineNumber;
        this.exceptionClass = builder.exceptionClass;
        this.stackTrace = builder.stackTrace;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.rawLine = builder.rawLine;
        this.fileOffset = builder.fileOffset;
        this.format = builder.format;
    }

    // Getters
    public String getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public String getLoggerName() { return loggerName; }
    public String getThreadName() { return threadName; }
    public String getSourceServer() { return sourceServer; }
    public String getSourceFile() { return sourceFile; }
    public Integer getLineNumber() { return lineNumber; }
    public String getExceptionClass() { return exceptionClass; }
    public String getStackTrace() { return stackTrace; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getRawLine() { return rawLine; }
    public long getFileOffset() { return fileOffset; }
    public LogFormat getFormat() { return format; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private Instant timestamp;
        private LogLevel level = LogLevel.INFO;
        private String message;
        private String loggerName;
        private String threadName;
        private String sourceServer;
        private String sourceFile;
        private Integer lineNumber;
        private String exceptionClass;
        private String stackTrace;
        private Map<String, Object> metadata;
        private String rawLine;
        private long fileOffset;
        private LogFormat format = LogFormat.UNKNOWN;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(LogLevel level) {
            this.level = level;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder loggerName(String loggerName) {
            this.loggerName = loggerName;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder sourceServer(String sourceServer) {
            this.sourceServer = sourceServer;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder lineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder exceptionClass(String exceptionClass) {
            this.exceptionClass = exceptionClass;
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder rawLine(String rawLine) {
            this.rawLine = rawLine;
            return this;
        }

        public Builder fileOffset(long fileOffset) {
            this.fileOffset = fileOffset;
            return this;
        }

        public Builder format(LogFormat format) {
            this.format = format;
            return this;
        }

        public LogEntry build() {
            Objects.requireNonNull(message, "message cannot be null");
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (id == null) {
                id = java.util.UUID.randomUUID().toString();
            }
            return new LogEntry(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntry logEntry = (LogEntry) o;
        return Objects.equals(id, logEntry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "timestamp=" + timestamp +
                ", level=" + level +
                ", message='" + message + '\'' +
                ", sourceServer='" + sourceServer + '\'' +
                '}';
    }
}
