package com.enterprise.logviewer.indexing.service;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogFormat;
import com.enterprise.logviewer.core.domain.SearchQuery;
import com.enterprise.logviewer.core.domain.SearchResult;
import com.enterprise.logviewer.core.service.LogParserService;
import com.enterprise.logviewer.core.service.SearchService;
import com.enterprise.logviewer.indexing.lucene.LuceneIndexManager;
import com.enterprise.logviewer.indexing.lucene.QueryBuilder;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * High-performance search service using Apache Lucene and virtual threads.
 * Achieves sub-second search on 1GB+ log files through indexing.
 */
public class LuceneSearchService implements SearchService, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(LuceneSearchService.class);

    private final LuceneIndexManager indexManager;
    private final LogParserService parserService;
    private final QueryBuilder queryBuilder;
    private final ExecutorService virtualExecutor;

    public LuceneSearchService(Path indexPath, LogParserService parserService) throws IOException {
        this.indexManager = new LuceneIndexManager(indexPath);
        this.parserService = parserService;
        this.queryBuilder = new QueryBuilder();
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        logger.info("LuceneSearchService initialized with index at: {}", indexPath);
    }

    @Override
    public CompletableFuture<Void> indexFile(Path file, String sourceIdentifier) {
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                logger.info("Indexing file: {} (source: {})", file, sourceIdentifier);

                // Detect format
                LogFormat format = parserService.detectFormat(file);
                logger.info("Detected format: {}", format);

                // Parse and index in streaming fashion
                List<LogEntry> batch = new ArrayList<>(1000);
                try (Stream<LogEntry> stream = parserService.parse(file, format)) {
                    stream.forEach(entry -> {
                        // Set source server if not already set
                        if (entry.getSourceServer() == null) {
                            entry = LogEntry.builder()
                                .id(entry.getId())
                                .timestamp(entry.getTimestamp())
                                .level(entry.getLevel())
                                .message(entry.getMessage())
                                .loggerName(entry.getLoggerName())
                                .threadName(entry.getThreadName())
                                .sourceServer(sourceIdentifier)
                                .sourceFile(file.getFileName().toString())
                                .exceptionClass(entry.getExceptionClass())
                                .stackTrace(entry.getStackTrace())
                                .metadata(entry.getMetadata())
                                .rawLine(entry.getRawLine())
                                .format(entry.getFormat())
                                .build();
                        }

                        batch.add(entry);

                        // Index in batches for better performance
                        if (batch.size() >= 1000) {
                            try {
                                indexManager.indexEntries(batch);
                                batch.clear();
                            } catch (IOException e) {
                                logger.error("Failed to index batch", e);
                            }
                        }
                    });
                }

                // Index remaining entries
                if (!batch.isEmpty()) {
                    indexManager.indexEntries(batch);
                }

                indexManager.commit();

                long duration = System.currentTimeMillis() - startTime;
                logger.info("Indexed file {} in {}ms", file.getFileName(), duration);

            } catch (Exception e) {
                logger.error("Failed to index file: {}", file, e);
                throw new RuntimeException("Failed to index file", e);
            }
        }, virtualExecutor);
    }

    @Override
    public CompletableFuture<Void> indexEntry(LogEntry entry) {
        return CompletableFuture.runAsync(() -> {
            try {
                indexManager.indexEntry(entry);
            } catch (IOException e) {
                logger.error("Failed to index entry", e);
                throw new RuntimeException("Failed to index entry", e);
            }
        }, virtualExecutor);
    }

    @Override
    public CompletableFuture<SearchResult> search(SearchQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Build Lucene query
                Query luceneQuery = queryBuilder.build(query);
                logger.debug("Executing query: {}", luceneQuery);

                // Create sort (newest first by default)
                Sort sort = queryBuilder.createTimestampSort(true);

                // Execute search
                TopDocs topDocs = indexManager.search(luceneQuery, query.maxResults(), sort);

                // Convert to LogEntry objects
                List<LogEntry> entries = new ArrayList<>();
                Map<String, List<String>> highlights = new HashMap<>();

                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = indexManager.getDocument(scoreDoc.doc);
                    LogEntry entry = documentToLogEntry(doc);
                    entries.add(entry);

                    // Simple highlighting (can be enhanced with Lucene Highlighter)
                    if (query.text() != null && !query.text().isBlank()) {
                        List<String> highlightTexts = new ArrayList<>();
                        String message = entry.getMessage();
                        if (message != null && message.toLowerCase().contains(query.text().toLowerCase())) {
                            highlightTexts.add(message);
                        }
                        if (!highlightTexts.isEmpty()) {
                            highlights.put(entry.getId(), highlightTexts);
                        }
                    }
                }

                long searchTime = System.currentTimeMillis() - startTime;
                logger.info("Search completed in {}ms, found {} results", searchTime, topDocs.totalHits.value);

                return new SearchResult(
                    entries,
                    highlights,
                    Map.of(), // Facets can be added later
                    topDocs.totalHits.value,
                    searchTime
                );

            } catch (Exception e) {
                logger.error("Search failed", e);
                throw new RuntimeException("Search failed", e);
            }
        }, virtualExecutor);
    }

    @Override
    public IndexStats getIndexStats() {
        try {
            LuceneIndexManager.IndexStats stats = indexManager.getStats();
            return new IndexStats(
                stats.numDocs(),
                stats.sizeBytes(),
                stats.numFiles(),
                stats.deletedDocs() == 0
            );
        } catch (IOException e) {
            logger.error("Failed to get index stats", e);
            return new IndexStats(0, 0, 0, false);
        }
    }

    @Override
    public CompletableFuture<Void> optimizeIndex() {
        return CompletableFuture.runAsync(() -> {
            try {
                indexManager.optimize();
            } catch (IOException e) {
                logger.error("Failed to optimize index", e);
                throw new RuntimeException("Failed to optimize index", e);
            }
        }, virtualExecutor);
    }

    @Override
    public void clearIndex() {
        try {
            indexManager.clearIndex();
        } catch (IOException e) {
            logger.error("Failed to clear index", e);
            throw new RuntimeException("Failed to clear index", e);
        }
    }

    private LogEntry documentToLogEntry(Document doc) {
        LogEntry.Builder builder = LogEntry.builder();

        // Required fields
        String id = doc.get(LuceneIndexManager.FIELD_ID);
        if (id != null) builder.id(id);

        String message = doc.get(LuceneIndexManager.FIELD_MESSAGE);
        if (message != null) builder.message(message);

        // Timestamp
        Long timestampMillis = doc.getField(LuceneIndexManager.FIELD_TIMESTAMP) != null
            ? doc.getField(LuceneIndexManager.FIELD_TIMESTAMP).numericValue().longValue()
            : null;
        if (timestampMillis != null) {
            builder.timestamp(Instant.ofEpochMilli(timestampMillis));
        }

        // Level
        String level = doc.get(LuceneIndexManager.FIELD_LEVEL);
        if (level != null) {
            builder.level(com.enterprise.logviewer.core.domain.LogLevel.valueOf(level));
        }

        // Optional fields
        String logger = doc.get(LuceneIndexManager.FIELD_LOGGER);
        if (logger != null) builder.loggerName(logger);

        String thread = doc.get(LuceneIndexManager.FIELD_THREAD);
        if (thread != null) builder.threadName(thread);

        String server = doc.get(LuceneIndexManager.FIELD_SERVER);
        if (server != null) builder.sourceServer(server);

        String exception = doc.get(LuceneIndexManager.FIELD_EXCEPTION);
        if (exception != null) builder.exceptionClass(exception);

        String stackTrace = doc.get(LuceneIndexManager.FIELD_STACK_TRACE);
        if (stackTrace != null) builder.stackTrace(stackTrace);

        String raw = doc.get(LuceneIndexManager.FIELD_RAW);
        if (raw != null) builder.rawLine(raw);

        return builder.build();
    }

    @Override
    public void close() throws Exception {
        virtualExecutor.close();
        indexManager.close();
        logger.info("LuceneSearchService closed");
    }
}
