package com.enterprise.logviewer.indexing.lucene;

import com.enterprise.logviewer.core.domain.LogEntry;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages Lucene index for log entries.
 * Thread-safe implementation optimized for virtual threads.
 * Uses ReentrantReadWriteLock instead of synchronized for better virtual thread compatibility.
 */
public class LuceneIndexManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexManager.class);

    // Field names
    public static final String FIELD_ID = "id";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_LEVEL = "level";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_LOGGER = "logger";
    public static final String FIELD_THREAD = "thread";
    public static final String FIELD_SERVER = "server";
    public static final String FIELD_EXCEPTION = "exception";
    public static final String FIELD_STACK_TRACE = "stackTrace";
    public static final String FIELD_RAW = "raw";

    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Create index manager with specified index directory.
     */
    public LuceneIndexManager(Path indexPath) throws IOException {
        this.directory = FSDirectory.open(indexPath);
        this.analyzer = new StandardAnalyzer();

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(256.0); // Use 256MB RAM buffer for better performance
        config.setCommitOnClose(true);

        this.writer = new IndexWriter(directory, config);
        this.searcherManager = new SearcherManager(writer, true, true, null);

        logger.info("Lucene index manager initialized at: {}", indexPath);
    }

    /**
     * Index a single log entry.
     * Uses virtual thread-compatible locking.
     */
    public void indexEntry(LogEntry entry) throws IOException {
        lock.writeLock().lock();
        try {
            Document doc = createDocument(entry);
            writer.addDocument(doc);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Index multiple entries efficiently.
     */
    public void indexEntries(Iterable<LogEntry> entries) throws IOException {
        lock.writeLock().lock();
        try {
            for (LogEntry entry : entries) {
                Document doc = createDocument(entry);
                writer.addDocument(doc);
            }
            writer.commit();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Search the index with given query.
     * Thread-safe for concurrent searches.
     */
    public TopDocs search(Query query, int maxResults) throws IOException {
        lock.readLock().lock();
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                return searcher.search(query, maxResults);
            } finally {
                searcherManager.release(searcher);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Search with sorting.
     */
    public TopDocs search(Query query, int maxResults, Sort sort) throws IOException {
        lock.readLock().lock();
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                return searcher.search(query, maxResults, sort);
            } finally {
                searcherManager.release(searcher);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get document by Lucene document ID.
     */
    public Document getDocument(int docId) throws IOException {
        lock.readLock().lock();
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                return searcher.doc(docId);
            } finally {
                searcherManager.release(searcher);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Commit changes to disk.
     */
    public void commit() throws IOException {
        lock.writeLock().lock();
        try {
            writer.commit();
            searcherManager.maybeRefresh();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Optimize index for better search performance.
     * Warning: This can take significant time for large indexes.
     */
    public void optimize() throws IOException {
        lock.writeLock().lock();
        try {
            logger.info("Optimizing index...");
            writer.forceMerge(1); // Merge all segments into one
            writer.commit();
            logger.info("Index optimization complete");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get index statistics.
     */
    public IndexStats getStats() throws IOException {
        lock.readLock().lock();
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                IndexReader reader = searcher.getIndexReader();
                return new IndexStats(
                    reader.numDocs(),
                    reader.maxDoc() - reader.numDocs(), // deletedDocs
                    directory.listAll().length,
                    calculateIndexSize()
                );
            } finally {
                searcherManager.release(searcher);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private long calculateIndexSize() throws IOException {
        long size = 0;
        for (String file : directory.listAll()) {
            size += directory.fileLength(file);
        }
        return size;
    }

    /**
     * Clear entire index.
     */
    public void clearIndex() throws IOException {
        lock.writeLock().lock();
        try {
            writer.deleteAll();
            writer.commit();
            logger.info("Index cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Document createDocument(LogEntry entry) {
        Document doc = new Document();

        // ID - stored, not indexed
        doc.add(new StoredField(FIELD_ID, entry.getId()));

        // Timestamp - indexed and stored for range queries and sorting
        if (entry.getTimestamp() != null) {
            doc.add(new LongPoint(FIELD_TIMESTAMP, entry.getTimestamp().toEpochMilli()));
            doc.add(new StoredField(FIELD_TIMESTAMP, entry.getTimestamp().toEpochMilli()));
            doc.add(new NumericDocValuesField(FIELD_TIMESTAMP, entry.getTimestamp().toEpochMilli()));
        }

        // Level - indexed and stored
        if (entry.getLevel() != null) {
            doc.add(new StringField(FIELD_LEVEL, entry.getLevel().name(), Field.Store.YES));
        }

        // Message - full-text indexed and stored
        if (entry.getMessage() != null) {
            doc.add(new TextField(FIELD_MESSAGE, entry.getMessage(), Field.Store.YES));
        }

        // Logger - indexed and stored
        if (entry.getLoggerName() != null) {
            doc.add(new StringField(FIELD_LOGGER, entry.getLoggerName(), Field.Store.YES));
        }

        // Thread - indexed and stored
        if (entry.getThreadName() != null) {
            doc.add(new StringField(FIELD_THREAD, entry.getThreadName(), Field.Store.YES));
        }

        // Server - indexed and stored
        if (entry.getSourceServer() != null) {
            doc.add(new StringField(FIELD_SERVER, entry.getSourceServer(), Field.Store.YES));
        }

        // Exception - indexed and stored
        if (entry.getExceptionClass() != null) {
            doc.add(new StringField(FIELD_EXCEPTION, entry.getExceptionClass(), Field.Store.YES));
        }

        // Stack trace - full-text indexed and stored
        if (entry.getStackTrace() != null) {
            doc.add(new TextField(FIELD_STACK_TRACE, entry.getStackTrace(), Field.Store.YES));
        }

        // Raw line - stored only (not indexed to save space)
        if (entry.getRawLine() != null) {
            doc.add(new StoredField(FIELD_RAW, entry.getRawLine()));
        }

        return doc;
    }

    @Override
    public void close() throws IOException {
        try {
            searcherManager.close();
            writer.close();
            directory.close();
            analyzer.close();
            logger.info("Lucene index manager closed");
        } catch (IOException e) {
            logger.error("Error closing index manager", e);
            throw e;
        }
    }

    /**
     * Index statistics record.
     */
    public record IndexStats(
        long numDocs,
        long deletedDocs,
        int numFiles,
        long sizeBytes
    ) {}
}
