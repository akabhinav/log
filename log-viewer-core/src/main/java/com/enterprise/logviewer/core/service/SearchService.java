package com.enterprise.logviewer.core.service;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.SearchQuery;
import com.enterprise.logviewer.core.domain.SearchResult;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance search service with indexing support.
 * Implementations should use Apache Lucene and virtual threads.
 */
public interface SearchService {

    /**
     * Index a log file for fast searching.
     * Returns CompletableFuture for async indexing.
     */
    CompletableFuture<Void> indexFile(Path file, String sourceIdentifier);

    /**
     * Index a single log entry.
     */
    CompletableFuture<Void> indexEntry(LogEntry entry);

    /**
     * Execute search query across all indexed logs.
     * Should return results in sub-second time even for 1GB+ files.
     */
    CompletableFuture<SearchResult> search(SearchQuery query);

    /**
     * Get index statistics (size, entry count, etc.).
     */
    IndexStats getIndexStats();

    /**
     * Optimize index for better search performance.
     */
    CompletableFuture<Void> optimizeIndex();

    /**
     * Clear all indexes.
     */
    void clearIndex();

    /**
     * Index statistics holder.
     */
    record IndexStats(
        long totalEntries,
        long indexSizeBytes,
        int segmentCount,
        boolean optimized
    ) {}
}
