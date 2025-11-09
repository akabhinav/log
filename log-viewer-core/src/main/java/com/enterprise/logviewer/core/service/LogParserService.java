package com.enterprise.logviewer.core.service;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Service for parsing log files in various formats.
 * Implementations should use streaming parsers for large files.
 */
public interface LogParserService {

    /**
     * Auto-detect log format from file content.
     */
    LogFormat detectFormat(Path file);

    /**
     * Parse log file and return stream of log entries.
     * Uses lazy evaluation for memory efficiency.
     */
    Stream<LogEntry> parse(Path file, LogFormat format);

    /**
     * Parse from input stream (for remote logs).
     */
    Stream<LogEntry> parse(InputStream input, LogFormat format, String sourceIdentifier);

    /**
     * Parse a single log line.
     */
    LogEntry parseLine(String line, LogFormat format);

    /**
     * Check if parser supports given format.
     */
    boolean supports(LogFormat format);
}
