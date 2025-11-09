package com.enterprise.logviewer.core.repository;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.SearchQuery;
import com.enterprise.logviewer.core.domain.SearchResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Repository interface for log storage and retrieval.
 * Implementations should use virtual threads for I/O operations.
 */
public interface LogRepository {

    /**
     * Save a single log entry.
     */
    CompletableFuture<Void> save(LogEntry entry);

    /**
     * Save multiple log entries in batch.
     */
    CompletableFuture<Void> saveAll(Stream<LogEntry> entries);

    /**
     * Find log entry by ID.
     */
    CompletableFuture<Optional<LogEntry>> findById(String id);

    /**
     * Search logs with given query.
     */
    CompletableFuture<SearchResult> search(SearchQuery query);

    /**
     * Get total count of logs from a source.
     */
    CompletableFuture<Long> count(String sourceServer);

    /**
     * Delete all logs from a source.
     */
    CompletableFuture<Void> deleteBySource(String sourceServer);

    /**
     * Stream all logs (for export or analysis).
     * Returns lazy stream for memory efficiency.
     */
    Stream<LogEntry> streamAll();
}
