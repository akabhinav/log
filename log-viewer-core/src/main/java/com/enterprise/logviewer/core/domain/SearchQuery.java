package com.enterprise.logviewer.core.domain;

import java.time.Instant;
import java.util.Set;

/**
 * Represents a search query with all filter criteria.
 * Immutable value object.
 */
public record SearchQuery(
    String text,
    Set<LogLevel> levels,
    Instant startTime,
    Instant endTime,
    String sourceServer,
    String loggerName,
    String threadName,
    boolean fuzzy,
    boolean regex,
    boolean caseSensitive,
    int maxResults,
    int offset
) {

    public SearchQuery {
        // Defensive copies for mutable collections
        levels = levels != null ? Set.copyOf(levels) : Set.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private Set<LogLevel> levels = Set.of();
        private Instant startTime;
        private Instant endTime;
        private String sourceServer;
        private String loggerName;
        private String threadName;
        private boolean fuzzy = false;
        private boolean regex = false;
        private boolean caseSensitive = false;
        private int maxResults = 1000;
        private int offset = 0;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder levels(Set<LogLevel> levels) {
            this.levels = levels;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder sourceServer(String sourceServer) {
            this.sourceServer = sourceServer;
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

        public Builder fuzzy(boolean fuzzy) {
            this.fuzzy = fuzzy;
            return this;
        }

        public Builder regex(boolean regex) {
            this.regex = regex;
            return this;
        }

        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(
                text, levels, startTime, endTime, sourceServer,
                loggerName, threadName, fuzzy, regex, caseSensitive,
                maxResults, offset
            );
        }
    }
}
