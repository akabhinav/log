package com.enterprise.logviewer.indexing.lucene;

import com.enterprise.logviewer.core.domain.LogLevel;
import com.enterprise.logviewer.core.domain.SearchQuery;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds Lucene queries from SearchQuery domain objects.
 * Supports complex queries with multiple criteria.
 */
public class QueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(QueryBuilder.class);

    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    /**
     * Build Lucene query from SearchQuery.
     */
    public Query build(SearchQuery searchQuery) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasAnyClause = false;

        // Text search in message and stack trace
        if (searchQuery.text() != null && !searchQuery.text().isBlank()) {
            Query textQuery = buildTextQuery(searchQuery.text(), searchQuery.fuzzy(), searchQuery.regex());
            builder.add(textQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // Log level filter
        if (!searchQuery.levels().isEmpty()) {
            Query levelQuery = buildLevelQuery(searchQuery.levels());
            builder.add(levelQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // Time range filter
        if (searchQuery.startTime() != null || searchQuery.endTime() != null) {
            Query timeQuery = buildTimeRangeQuery(
                searchQuery.startTime() != null ? searchQuery.startTime().toEpochMilli() : Long.MIN_VALUE,
                searchQuery.endTime() != null ? searchQuery.endTime().toEpochMilli() : Long.MAX_VALUE
            );
            builder.add(timeQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // Server filter
        if (searchQuery.sourceServer() != null && !searchQuery.sourceServer().isBlank()) {
            Query serverQuery = new TermQuery(new Term(LuceneIndexManager.FIELD_SERVER, searchQuery.sourceServer()));
            builder.add(serverQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // Logger filter
        if (searchQuery.loggerName() != null && !searchQuery.loggerName().isBlank()) {
            Query loggerQuery = buildWildcardOrExactQuery(LuceneIndexManager.FIELD_LOGGER, searchQuery.loggerName());
            builder.add(loggerQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // Thread filter
        if (searchQuery.threadName() != null && !searchQuery.threadName().isBlank()) {
            Query threadQuery = new TermQuery(new Term(LuceneIndexManager.FIELD_THREAD, searchQuery.threadName()));
            builder.add(threadQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // If no clauses, return match-all query
        if (!hasAnyClause) {
            return new MatchAllDocsQuery();
        }

        return builder.build();
    }

    private Query buildTextQuery(String text, boolean fuzzy, boolean regex) {
        if (regex) {
            return buildRegexQuery(text);
        }

        if (fuzzy) {
            return buildFuzzyQuery(text);
        }

        // Standard full-text search across message and stack trace
        BooleanQuery.Builder multiFieldBuilder = new BooleanQuery.Builder();

        try {
            QueryParser messageParser = new QueryParser(LuceneIndexManager.FIELD_MESSAGE, analyzer);
            Query messageQuery = messageParser.parse(text);
            multiFieldBuilder.add(messageQuery, BooleanClause.Occur.SHOULD);

            QueryParser stackParser = new QueryParser(LuceneIndexManager.FIELD_STACK_TRACE, analyzer);
            Query stackQuery = stackParser.parse(text);
            multiFieldBuilder.add(stackQuery, BooleanClause.Occur.SHOULD);

        } catch (ParseException e) {
            logger.warn("Failed to parse query text, using wildcard: {}", text, e);
            return new WildcardQuery(new Term(LuceneIndexManager.FIELD_MESSAGE, "*" + text + "*"));
        }

        return multiFieldBuilder.build();
    }

    private Query buildFuzzyQuery(String text) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() > 2) { // Only apply fuzzy to words longer than 2 chars
                FuzzyQuery fuzzyMessage = new FuzzyQuery(
                    new Term(LuceneIndexManager.FIELD_MESSAGE, word.toLowerCase()),
                    2 // max edit distance
                );
                builder.add(fuzzyMessage, BooleanClause.Occur.SHOULD);
            }
        }

        return builder.build();
    }

    private Query buildRegexQuery(String pattern) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        RegexpQuery messageRegex = new RegexpQuery(new Term(LuceneIndexManager.FIELD_MESSAGE, pattern));
        builder.add(messageRegex, BooleanClause.Occur.SHOULD);

        RegexpQuery stackRegex = new RegexpQuery(new Term(LuceneIndexManager.FIELD_STACK_TRACE, pattern));
        builder.add(stackRegex, BooleanClause.Occur.SHOULD);

        return builder.build();
    }

    private Query buildLevelQuery(java.util.Set<LogLevel> levels) {
        if (levels.size() == 1) {
            LogLevel level = levels.iterator().next();
            return new TermQuery(new Term(LuceneIndexManager.FIELD_LEVEL, level.name()));
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (LogLevel level : levels) {
            builder.add(new TermQuery(new Term(LuceneIndexManager.FIELD_LEVEL, level.name())),
                       BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private Query buildTimeRangeQuery(long startMillis, long endMillis) {
        return LongPoint.newRangeQuery(LuceneIndexManager.FIELD_TIMESTAMP, startMillis, endMillis);
    }

    private Query buildWildcardOrExactQuery(String field, String value) {
        if (value.contains("*") || value.contains("?")) {
            return new WildcardQuery(new Term(field, value.toLowerCase()));
        }
        return new TermQuery(new Term(field, value));
    }

    /**
     * Create sort for timestamp descending (newest first).
     */
    public Sort createTimestampSort(boolean descending) {
        return new Sort(new SortField(LuceneIndexManager.FIELD_TIMESTAMP, SortField.Type.LONG, descending));
    }
}
