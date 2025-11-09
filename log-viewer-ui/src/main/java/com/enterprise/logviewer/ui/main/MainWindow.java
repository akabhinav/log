package com.enterprise.logviewer.ui.main;

import com.enterprise.logviewer.core.domain.*;
import com.enterprise.logviewer.core.service.LogParserService;
import com.enterprise.logviewer.core.service.SearchService;
import com.enterprise.logviewer.parsers.CompositeLogParserService;
import com.enterprise.logviewer.indexing.service.LuceneSearchService;
import com.enterprise.logviewer.aws.auth.AwsPclAuthService;
import com.enterprise.logviewer.ui.table.VirtualLogTableModel;
import com.enterprise.logviewer.ui.dialogs.AwsPclLoginDialog;
import com.formdev.flatlaf.FlatDarkLaf;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Main application window with dual-pane log viewer and search.
 */
public class MainWindow extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    // Services
    private final LogParserService parserService;
    private SearchService searchService;
    private AwsPclAuthService awsAuthService;

    // UI Components
    private final JTabbedPane tabbedPane;
    private final VirtualLogTableModel leftTableModel;
    private final VirtualLogTableModel rightTableModel;
    private final JTable leftTable;
    private final JTable rightTable;
    private final JTextField searchField;
    private final JComboBox<LogLevel> levelFilter;
    private final JButton searchButton;
    private final JButton indexButton;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    private Path currentIndexPath;

    public MainWindow() {
        super("Enterprise Log Viewer - High-Performance Multi-Server Analyzer");

        // Initialize services
        this.parserService = new CompositeLogParserService();
        this.currentIndexPath = Path.of(System.getProperty("user.home"), ".logviewer", "index");

        try {
            this.searchService = new LuceneSearchService(currentIndexPath, parserService);
        } catch (Exception e) {
            logger.error("Failed to initialize search service", e);
            JOptionPane.showMessageDialog(this,
                "Failed to initialize search engine: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Initialize AWS PCL auth service
        this.awsAuthService = new AwsPclAuthService();

        // Initialize UI components
        this.tabbedPane = new JTabbedPane();
        this.leftTableModel = new VirtualLogTableModel();
        this.rightTableModel = new VirtualLogTableModel();
        this.leftTable = createLogTable(leftTableModel);
        this.rightTable = createLogTable(rightTableModel);
        this.searchField = new JTextField(30);
        this.levelFilter = new JComboBox<>(LogLevel.values());
        this.searchButton = new JButton("Search");
        this.indexButton = new JButton("Index File");
        this.statusLabel = new JLabel("Ready");
        this.progressBar = new JProgressBar();

        initUI();
        setupEventHandlers();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

        logger.info("Main window initialized");
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Menu bar
        JMenuBar menuBar = createMenuBar();
        setJMenuBar(menuBar);

        // Top toolbar
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        // Center: Dual-pane or single pane viewer
        JPanel centerPanel = new JPanel(new MigLayout("fill, insets 0"));

        // Single pane view
        JScrollPane leftScrollPane = new JScrollPane(leftTable);
        centerPanel.add(leftScrollPane, "grow, push");

        tabbedPane.addTab("Log Viewer", centerPanel);

        // Dual pane view
        JPanel dualPane = createDualPaneView();
        tabbedPane.addTab("Dual Pane Compare", dualPane);

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom status bar
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open Log File...");
        JMenuItem indexItem = new JMenuItem("Index File...");
        JMenuItem exitItem = new JMenuItem("Exit");

        openItem.addActionListener(e -> openLogFile());
        indexItem.addActionListener(e -> indexFile());
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openItem);
        fileMenu.add(indexItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Tools menu
        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem awsPclLoginItem = new JMenuItem("AWS PCL Login...");
        JMenuItem eksItem = new JMenuItem("Fetch from EKS...");
        JMenuItem sshItem = new JMenuItem("Fetch from SSH...");
        JMenuItem clearIndexItem = new JMenuItem("Clear Index");

        awsPclLoginItem.addActionListener(e -> showAwsPclLoginDialog());
        eksItem.addActionListener(e -> showEKSDialog());
        sshItem.addActionListener(e -> showSSHDialog());
        clearIndexItem.addActionListener(e -> clearIndex());

        toolsMenu.add(awsPclLoginItem);
        toolsMenu.addSeparator();
        toolsMenu.add(eksItem);
        toolsMenu.add(sshItem);
        toolsMenu.addSeparator();
        toolsMenu.add(clearIndexItem);

        menuBar.add(fileMenu);
        menuBar.add(toolsMenu);

        return menuBar;
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new MigLayout("fillx, insets 5"));

        toolbar.add(new JLabel("Search:"), "");
        toolbar.add(searchField, "width 300!");
        toolbar.add(new JLabel("Level:"), "gapleft 10");
        toolbar.add(levelFilter, "width 100!");
        toolbar.add(searchButton, "gapleft 10");
        toolbar.add(indexButton, "gapleft 10");

        return toolbar;
    }

    private JPanel createDualPaneView() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(new JScrollPane(leftTable));
        splitPane.setRightComponent(new JScrollPane(rightTable));
        splitPane.setResizeWeight(0.5);

        panel.add(splitPane, "grow, push");

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new MigLayout("fillx, insets 2"));

        statusBar.add(statusLabel, "growx, pushx");
        statusBar.add(progressBar, "width 200!");

        progressBar.setVisible(false);

        return statusBar;
    }

    private JTable createLogTable(VirtualLogTableModel model) {
        JTable table = new JTable(model);

        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(180); // Timestamp
        table.getColumnModel().getColumn(1).setPreferredWidth(60);  // Level
        table.getColumnModel().getColumn(2).setPreferredWidth(150); // Logger
        table.getColumnModel().getColumn(3).setPreferredWidth(100); // Thread
        table.getColumnModel().getColumn(4).setPreferredWidth(600); // Message
        table.getColumnModel().getColumn(5).setPreferredWidth(100); // Server

        // Color-code log levels
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                          boolean isSelected, boolean hasFocus,
                                                          int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (value instanceof LogLevel level) {
                    setForeground(Color.decode(level.getColorCode()));
                    setFont(getFont().deriveFont(Font.BOLD));
                }

                return this;
            }
        });

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight(true);

        return table;
    }

    private void setupEventHandlers() {
        searchButton.addActionListener(e -> executeSearch());
        indexButton.addActionListener(e -> indexFile());

        searchField.addActionListener(e -> executeSearch());
    }

    private void openLogFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            loadLogFile(file.toPath());
        }
    }

    private void loadLogFile(Path file) {
        setStatus("Loading log file: " + file.getFileName());
        showProgress(true);

        CompletableFuture.runAsync(() -> {
            try {
                LogFormat format = parserService.detectFormat(file);
                logger.info("Detected format: {}", format);

                parserService.parse(file, format)
                    .limit(10000) // Limit to first 10K entries for quick view
                    .forEach(entry -> SwingUtilities.invokeLater(() ->
                        leftTableModel.addEntries(java.util.List.of(entry))
                    ));

                SwingUtilities.invokeLater(() -> {
                    setStatus("Loaded " + leftTableModel.getEntryCount() + " entries from " + file.getFileName());
                    showProgress(false);
                });

            } catch (Exception e) {
                logger.error("Failed to load file", e);
                SwingUtilities.invokeLater(() -> {
                    setStatus("Failed to load file: " + e.getMessage());
                    showProgress(false);
                    JOptionPane.showMessageDialog(this, "Failed to load file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void indexFile() {
        if (searchService == null) {
            JOptionPane.showMessageDialog(this, "Search service not initialized",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            Path filePath = file.toPath();

            setStatus("Indexing file: " + file.getName());
            showProgress(true);

            searchService.indexFile(filePath, file.getName())
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    setStatus("Indexed " + file.getName() + " successfully");
                    showProgress(false);
                    JOptionPane.showMessageDialog(this, "File indexed successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Indexing failed");
                        showProgress(false);
                        JOptionPane.showMessageDialog(this, "Failed to index file: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
        }
    }

    private void executeSearch() {
        if (searchService == null) {
            JOptionPane.showMessageDialog(this, "Search service not initialized",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        setStatus("Searching...");
        showProgress(true);

        LogLevel selectedLevel = (LogLevel) levelFilter.getSelectedItem();
        Set<LogLevel> levels = selectedLevel != null ? Set.of(selectedLevel) : Set.of();

        SearchQuery query = SearchQuery.builder()
            .text(searchText)
            .levels(levels)
            .maxResults(10000)
            .build();

        searchService.search(query)
            .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                leftTableModel.setEntries(result.entries());
                setStatus(String.format("Found %d results in %dms",
                    result.totalHits(), result.searchTimeMs()));
                showProgress(false);
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Search failed");
                    showProgress(false);
                    JOptionPane.showMessageDialog(this, "Search failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
                return null;
            });
    }

    private void clearIndex() {
        if (searchService != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Clear entire index?", "Confirm", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                searchService.clearIndex();
                setStatus("Index cleared");
            }
        }
    }

    private void showAwsPclLoginDialog() {
        logger.info("Opening AWS PCL Login dialog");

        AwsPclLoginDialog dialog = new AwsPclLoginDialog(this, awsAuthService);

        // Listen for log downloads
        dialog.addPropertyChangeListener("logs-downloaded", evt -> {
            Path downloadPath = (Path) evt.getNewValue();
            logger.info("Logs downloaded to: {}", downloadPath);

            // Auto-index downloaded logs
            if (searchService != null && downloadPath != null) {
                try {
                    java.io.File[] logFiles = downloadPath.toFile().listFiles((dir, name) -> name.endsWith(".log"));

                    if (logFiles != null && logFiles.length > 0) {
                        setStatus("Indexing downloaded logs...");
                        showProgress(true);

                        for (java.io.File logFile : logFiles) {
                            searchService.indexFile(logFile.toPath(), logFile.getName())
                                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                                    setStatus("Indexed: " + logFile.getName());
                                }));
                        }

                        // Final status update
                        CompletableFuture.allOf(
                            java.util.Arrays.stream(logFiles)
                                .map(f -> searchService.indexFile(f.toPath(), f.getName()))
                                .toArray(CompletableFuture[]::new)
                        ).thenRun(() -> SwingUtilities.invokeLater(() -> {
                            showProgress(false);
                            setStatus("Indexed " + logFiles.length + " log files from EKS");
                            JOptionPane.showMessageDialog(this,
                                "Successfully indexed " + logFiles.length + " log files.\n" +
                                "You can now search the logs.",
                                "Indexing Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                        }));
                    }
                } catch (Exception e) {
                    logger.error("Failed to index downloaded logs", e);
                    SwingUtilities.invokeLater(() -> {
                        showProgress(false);
                        JOptionPane.showMessageDialog(this,
                            "Failed to index logs: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
        });

        dialog.setVisible(true);

        if (dialog.isAuthenticated()) {
            setStatus("✓ Authenticated with AWS PCL");
            logger.info("User successfully authenticated with AWS PCL");
        }
    }

    private void showEKSDialog() {
        if (!awsAuthService.isAuthenticated()) {
            JOptionPane.showMessageDialog(this,
                "Please login with AWS PCL first.\nTools → AWS PCL Login",
                "Authentication Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
            "EKS log fetching is now available through AWS PCL Login!\n" +
            "Use Tools → AWS PCL Login to authenticate and download logs.",
            "EKS Integration", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showSSHDialog() {
        JOptionPane.showMessageDialog(this,
            "SSH log fetching - Coming soon!\nUse the SshLogFetcher class programmatically.",
            "SSH Integration", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        logger.info("Status: {}", message);
    }

    private void showProgress(boolean show) {
        progressBar.setVisible(show);
        if (show) {
            progressBar.setIndeterminate(true);
        }
    }

    public static void main(String[] args) {
        // Set FlatLaf dark theme
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            logger.error("Failed to set look and feel", e);
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
