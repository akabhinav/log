package com.enterprise.logviewer.parsers;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.service.LogParserService;
import com.enterprise.logviewer.parsers.detector.LogFormatDetector;
import com.enterprise.logviewer.parsers.json.JsonLogParser;
import com.enterprise.logviewer.parsers.log4j.Log4jPatternParser;
import com.enterprise.logviewer.parsers.syslog.SyslogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Composite parser service that delegates to specific parsers based on format.
 * Supports auto-detection and all major log formats.
 */
public class CompositeLogParserService implements LogParserService {

    private static final Logger logger = LoggerFactory.getLogger(CompositeLogParserService.class);

    private final Map<LogFormat, LogParserService> parsers = new HashMap<>();

    public CompositeLogParserService() {
        registerDefaultParsers();
    }

    private void registerDefaultParsers() {
        // JSON-based formats
        JsonLogParser jsonParser = new JsonLogParser();
        parsers.put(LogFormat.JSON, jsonParser);
        parsers.put(LogFormat.LOGSTASH_JSON, jsonParser);
        parsers.put(LogFormat.AWS_CLOUDWATCH, jsonParser);
        parsers.put(LogFormat.DOCKER, jsonParser);
        parsers.put(LogFormat.MONGODB, jsonParser);
        parsers.put(LogFormat.ELASTICSEARCH, jsonParser);

        // Java logging formats
        Log4jPatternParser log4jParser = new Log4jPatternParser();
        parsers.put(LogFormat.LOG4J, log4jParser);
        parsers.put(LogFormat.LOG4J2, log4jParser);
        parsers.put(LogFormat.LOGBACK, log4jParser);
        parsers.put(LogFormat.SPRING_BOOT, log4jParser);
        parsers.put(LogFormat.SLF4J, log4jParser);

        // Syslog formats
        SyslogParser syslogParser = new SyslogParser();
        parsers.put(LogFormat.SYSLOG, syslogParser);
        parsers.put(LogFormat.SYSLOG_RFC3164, syslogParser);

        // Kubernetes
        parsers.put(LogFormat.KUBERNETES_POD, log4jParser); // K8s logs often use structured format

        logger.info("Registered {} parsers for {} formats",
                    parsers.values().stream().distinct().count(),
                    parsers.size());
    }

    /**
     * Register a custom parser for a specific format.
     */
    public void registerParser(LogFormat format, LogParserService parser) {
        parsers.put(format, parser);
        logger.info("Registered custom parser for format: {}", format);
    }

    @Override
    public LogFormat detectFormat(Path file) {
        return LogFormatDetector.detectFormat(file);
    }

    @Override
    public Stream<LogEntry> parse(Path file, LogFormat format) {
        LogFormat detectedFormat = format;

        // Auto-detect if unknown
        if (format == LogFormat.UNKNOWN) {
            detectedFormat = detectFormat(file);
            logger.info("Auto-detected format for {}: {}", file.getFileName(), detectedFormat);
        }

        LogParserService parser = getParser(detectedFormat);
        return parser.parse(file, detectedFormat);
    }

    @Override
    public Stream<LogEntry> parse(InputStream input, LogFormat format, String sourceIdentifier) {
        LogFormat actualFormat = format != LogFormat.UNKNOWN ? format : LogFormat.LOG4J;
        LogParserService parser = getParser(actualFormat);
        return parser.parse(input, actualFormat, sourceIdentifier);
    }

    @Override
    public LogEntry parseLine(String line, LogFormat format) {
        LogParserService parser = getParser(format);
        return parser.parseLine(line, format);
    }

    @Override
    public boolean supports(LogFormat format) {
        return parsers.containsKey(format);
    }

    private LogParserService getParser(LogFormat format) {
        LogParserService parser = parsers.get(format);

        if (parser == null) {
            logger.warn("No parser registered for format {}, using Log4j parser as fallback", format);
            parser = parsers.get(LogFormat.LOG4J);
        }

        return parser;
    }

    /**
     * Get all supported formats.
     */
    public java.util.Set<LogFormat> getSupportedFormats() {
        return parsers.keySet();
    }
}
