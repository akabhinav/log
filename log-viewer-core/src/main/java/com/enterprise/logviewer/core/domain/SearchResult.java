package com.enterprise.logviewer.core.domain;

import java.util.List;
import java.util.Map;

/**
 * Represents search results with highlighting and facets.
 */
public record SearchResult(
    List<LogEntry> entries,
    Map<String, List<String>> highlights,
    Map<String, Long> facets,
    long totalHits,
    long searchTimeMs
) {
    public SearchResult {
        entries = entries != null ? List.copyOf(entries) : List.of();
        highlights = highlights != null ? Map.copyOf(highlights) : Map.of();
        facets = facets != null ? Map.copyOf(facets) : Map.of();
    }
}
