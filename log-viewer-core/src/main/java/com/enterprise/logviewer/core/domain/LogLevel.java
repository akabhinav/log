package com.enterprise.logviewer.core.domain;

/**
 * Standard log levels supported across all log formats.
 * Ordered by severity (highest to lowest).
 */
public enum LogLevel {
    FATAL(6, "FATAL"),
    ERROR(5, "ERROR"),
    WARN(4, "WARN"),
    INFO(3, "INFO"),
    DEBUG(2, "DEBUG"),
    TRACE(1, "TRACE"),
    UNKNOWN(0, "UNKNOWN");

    private final int severity;
    private final String displayName;

    LogLevel(int severity, String displayName) {
        this.severity = severity;
        this.displayName = displayName;
    }

    public int getSeverity() {
        return severity;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse log level from string with fuzzy matching.
     * Supports common variations like "ERR", "WARNING", "WRN", etc.
     */
    public static LogLevel fromString(String level) {
        if (level == null || level.isBlank()) {
            return UNKNOWN;
        }

        String normalized = level.trim().toUpperCase();

        return switch (normalized) {
            case "FATAL", "CRITICAL", "CRIT", "SEVERE" -> FATAL;
            case "ERROR", "ERR", "E" -> ERROR;
            case "WARN", "WARNING", "WRN", "W" -> WARN;
            case "INFO", "INFORMATION", "I" -> INFO;
            case "DEBUG", "DBG", "D" -> DEBUG;
            case "TRACE", "TRC", "T", "VERBOSE", "V" -> TRACE;
            default -> UNKNOWN;
        };
    }

    /**
     * Get color code for UI display.
     */
    public String getColorCode() {
        return switch (this) {
            case FATAL, ERROR -> "#FF0000";  // Red
            case WARN -> "#FFA500";          // Orange
            case INFO -> "#00AA00";          // Green
            case DEBUG -> "#808080";         // Gray
            case TRACE -> "#C0C0C0";         // Light Gray
            case UNKNOWN -> "#000000";       // Black
        };
    }
}
