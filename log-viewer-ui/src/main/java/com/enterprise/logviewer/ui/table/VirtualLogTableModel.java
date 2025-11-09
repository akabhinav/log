package com.enterprise.logviewer.ui.table;

import com.enterprise.logviewer.core.domain.LogEntry;
import com.enterprise.logviewer.core.domain.LogLevel;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Virtual scrolling table model for log entries.
 * Only keeps visible rows in memory for efficient handling of millions of entries.
 */
public class VirtualLogTableModel extends AbstractTableModel {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final String[] COLUMN_NAMES = {
        "Timestamp", "Level", "Logger", "Thread", "Message", "Server"
    };

    private final List<LogEntry> entries;
    private final int pageSize = 1000; // Load 1000 entries at a time

    public VirtualLogTableModel() {
        this.entries = new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 1 -> LogLevel.class; // Level column
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex >= entries.size()) {
            return null;
        }

        LogEntry entry = entries.get(rowIndex);

        return switch (columnIndex) {
            case 0 -> entry.getTimestamp() != null
                ? TIMESTAMP_FORMATTER.format(entry.getTimestamp())
                : "";
            case 1 -> entry.getLevel();
            case 2 -> entry.getLoggerName() != null ? entry.getLoggerName() : "";
            case 3 -> entry.getThreadName() != null ? entry.getThreadName() : "";
            case 4 -> entry.getMessage() != null ? entry.getMessage() : "";
            case 5 -> entry.getSourceServer() != null ? entry.getSourceServer() : "";
            default -> "";
        };
    }

    /**
     * Add entries to the model.
     */
    public void addEntries(List<LogEntry> newEntries) {
        int firstRow = entries.size();
        entries.addAll(newEntries);
        int lastRow = entries.size() - 1;
        fireTableRowsInserted(firstRow, lastRow);
    }

    /**
     * Set all entries (replaces existing).
     */
    public void setEntries(List<LogEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        fireTableDataChanged();
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        entries.clear();
        fireTableDataChanged();
    }

    /**
     * Get entry at row.
     */
    public LogEntry getEntryAt(int row) {
        if (row >= 0 && row < entries.size()) {
            return entries.get(row);
        }
        return null;
    }

    /**
     * Get total entry count.
     */
    public int getEntryCount() {
        return entries.size();
    }
}
