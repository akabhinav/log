package com.enterprise.logviewer.parsers;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.service.LogParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Abstract base class for log parsers providing common functionality.
 */
public abstract class AbstractLogParser implements LogParserService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected abstract LogFormat getSupportedFormat();

    @Override
    public boolean supports(LogFormat format) {
        return getSupportedFormat() == format;
    }

    @Override
    public Stream<LogEntry> parse(Path file, LogFormat format) {
        Objects.requireNonNull(file, "file cannot be null");

        if (!supports(format)) {
            throw new UnsupportedOperationException(
                "Parser " + getClass().getSimpleName() + " does not support format " + format
            );
        }

        try {
            InputStream input = Files.newInputStream(file);
            String sourceId = file.getFileName().toString();
            return parse(input, format, sourceId);
        } catch (IOException e) {
            logger.error("Failed to open file: {}", file, e);
            return Stream.empty();
        }
    }

    @Override
    public Stream<LogEntry> parse(InputStream input, LogFormat format, String sourceIdentifier) {
        Objects.requireNonNull(input, "input cannot be null");

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));

        return reader.lines()
            .map(line -> {
                try {
                    return parseLine(line, format);
                } catch (Exception e) {
                    logger.warn("Failed to parse line: {}", line, e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .onClose(() -> {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.error("Failed to close reader", e);
                }
            });
    }

    @Override
    public LogFormat detectFormat(Path file) {
        // Default implementation - can be overridden
        return LogFormat.UNKNOWN;
    }

    /**
     * Extract exception information from stack trace text.
     */
    protected record ExceptionInfo(String exceptionClass, String stackTrace) {}

    /**
     * Parse exception from multi-line text.
     */
    protected ExceptionInfo parseException(String text) {
        if (text == null || !text.contains("Exception")) {
            return null;
        }

        String[] lines = text.split("\n");
        String exceptionClass = null;

        for (String line : lines) {
            line = line.trim();
            if (line.contains("Exception") || line.contains("Error")) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    exceptionClass = line.substring(0, colonIndex).trim();
                } else {
                    exceptionClass = line.trim();
                }
                break;
            }
        }

        return new ExceptionInfo(exceptionClass, text);
    }
}
