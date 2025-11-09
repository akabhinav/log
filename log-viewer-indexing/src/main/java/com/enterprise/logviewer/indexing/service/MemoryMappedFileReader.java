package com.enterprise.logviewer.indexing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Memory-mapped file reader for efficient reading of large (1GB+) log files.
 * Uses zero-copy I/O with MappedByteBuffer for optimal performance.
 * Designed to work seamlessly with virtual threads.
 */
public class MemoryMappedFileReader implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMappedFileReader.class);

    // Map files in 128MB chunks to avoid large continuous memory allocation
    private static final long CHUNK_SIZE = 128 * 1024 * 1024; // 128MB

    private final Path file;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel fileChannel;
    private final long fileSize;
    private final List<MappedByteBuffer> mappedBuffers;

    /**
     * Create memory-mapped reader for specified file.
     */
    public MemoryMappedFileReader(Path file) throws IOException {
        this.file = file;
        this.randomAccessFile = new RandomAccessFile(file.toFile(), "r");
        this.fileChannel = randomAccessFile.getChannel();
        this.fileSize = fileChannel.size();
        this.mappedBuffers = new ArrayList<>();

        mapFile();

        logger.info("Memory-mapped file reader created for {} (size: {} MB)",
                   file.getFileName(), fileSize / (1024 * 1024));
    }

    private void mapFile() throws IOException {
        long position = 0;

        while (position < fileSize) {
            long remainingSize = fileSize - position;
            long mapSize = Math.min(CHUNK_SIZE, remainingSize);

            MappedByteBuffer buffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                position,
                mapSize
            );

            mappedBuffers.add(buffer);
            position += mapSize;
        }

        logger.debug("Mapped file into {} chunks", mappedBuffers.size());
    }

    /**
     * Read a line starting from the given file offset.
     * Returns null if offset is beyond file size.
     */
    public String readLineAt(long offset) throws IOException {
        if (offset >= fileSize) {
            return null;
        }

        // Find which buffer contains this offset
        int bufferIndex = (int) (offset / CHUNK_SIZE);
        int bufferOffset = (int) (offset % CHUNK_SIZE);

        if (bufferIndex >= mappedBuffers.size()) {
            return null;
        }

        MappedByteBuffer buffer = mappedBuffers.get(bufferIndex);
        StringBuilder line = new StringBuilder();

        // Read from current buffer
        buffer.position(bufferOffset);
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\n') {
                break;
            }
            if (b != '\r') { // Skip carriage return
                line.append((char) b);
            }
        }

        // If we hit buffer boundary and didn't find newline, continue in next buffer
        if (!buffer.hasRemaining() && bufferIndex + 1 < mappedBuffers.size()) {
            MappedByteBuffer nextBuffer = mappedBuffers.get(bufferIndex + 1);
            nextBuffer.position(0);
            while (nextBuffer.hasRemaining()) {
                byte b = nextBuffer.get();
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    line.append((char) b);
                }
            }
        }

        return line.toString();
    }

    /**
     * Read multiple lines starting from offset.
     * Useful for virtual scrolling in UI.
     */
    public List<String> readLines(long startOffset, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>(maxLines);
        long currentOffset = startOffset;

        for (int i = 0; i < maxLines; i++) {
            String line = readLineAt(currentOffset);
            if (line == null) {
                break; // Reached end of file
            }
            lines.add(line);

            // Calculate next line offset (current offset + line length + newline char)
            currentOffset += line.getBytes(StandardCharsets.UTF_8).length + 1;

            if (currentOffset >= fileSize) {
                break;
            }
        }

        return lines;
    }

    /**
     * Find the offset of the start of a line containing the search text.
     * Starts searching from given offset.
     * Returns -1 if not found.
     */
    public long findNext(String searchText, long fromOffset) throws IOException {
        long currentOffset = fromOffset;

        while (currentOffset < fileSize) {
            String line = readLineAt(currentOffset);
            if (line == null) {
                break;
            }

            if (line.contains(searchText)) {
                return currentOffset;
            }

            // Move to next line
            currentOffset += line.getBytes(StandardCharsets.UTF_8).length + 1;
        }

        return -1; // Not found
    }

    /**
     * Get total file size.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Get estimated line count (approximate).
     * Samples first megabyte to estimate average line length.
     */
    public long estimateLineCount() throws IOException {
        long sampleSize = Math.min(1024 * 1024, fileSize); // 1MB sample
        int lineCount = 0;
        long offset = 0;

        while (offset < sampleSize) {
            String line = readLineAt(offset);
            if (line == null) {
                break;
            }
            lineCount++;
            offset += line.getBytes(StandardCharsets.UTF_8).length + 1;
        }

        if (lineCount == 0) {
            return 0;
        }

        double avgLineLength = (double) sampleSize / lineCount;
        return (long) (fileSize / avgLineLength);
    }

    /**
     * Read a range of bytes from file.
     * Useful for binary data or custom parsing.
     */
    public byte[] readBytes(long offset, int length) throws IOException {
        if (offset >= fileSize) {
            return new byte[0];
        }

        int actualLength = (int) Math.min(length, fileSize - offset);
        byte[] result = new byte[actualLength];

        int bufferIndex = (int) (offset / CHUNK_SIZE);
        int bufferOffset = (int) (offset % CHUNK_SIZE);

        int bytesRead = 0;
        while (bytesRead < actualLength && bufferIndex < mappedBuffers.size()) {
            MappedByteBuffer buffer = mappedBuffers.get(bufferIndex);
            buffer.position(bufferOffset);

            int toRead = Math.min(actualLength - bytesRead, buffer.remaining());
            buffer.get(result, bytesRead, toRead);

            bytesRead += toRead;
            bufferIndex++;
            bufferOffset = 0; // Start from beginning of next buffer
        }

        return result;
    }

    @Override
    public void close() throws IOException {
        try {
            // Unmap buffers (best effort)
            for (MappedByteBuffer buffer : mappedBuffers) {
                // Note: There's no explicit unmap in standard Java
                // Buffers will be garbage collected
                buffer.clear();
            }
            mappedBuffers.clear();

            fileChannel.close();
            randomAccessFile.close();

            logger.info("Memory-mapped file reader closed for {}", file.getFileName());
        } catch (IOException e) {
            logger.error("Error closing memory-mapped reader", e);
            throw e;
        }
    }
}
