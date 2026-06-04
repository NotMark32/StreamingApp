package com.streaming.gui;

import com.streaming.common.Protocol;
import com.streaming.server.StreamingServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ServerGUI extends JFrame {

    private StreamingServer server;
    private Thread serverThread;

    // Components
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JLabel clientCountLabel;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;

    public ServerGUI() {
        super("Streaming Server");
        initComponents();
        layoutComponents();
        setupActions();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        startBtn        = new JButton("▶ Start Server");
        stopBtn         = new JButton("■ Stop Server");
        statusLabel     = new JLabel("● Εκτός λειτουργίας");
        clientCountLabel= new JLabel("Ενεργοί Clients: 0");
        logArea         = new JTextArea();

        stopBtn.setEnabled(false);
        statusLabel.setForeground(Color.RED);

        // Πίνακας αρχείων
        String[] columns = {"Όνομα", "Format", "Ανάλυση"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        fileTable = new JTable(tableModel);

        // Log area
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // ── Top panel: controls ──────────────────────────────────────
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Έλεγχος Server"));
        topPanel.add(startBtn);
        topPanel.add(stopBtn);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(statusLabel);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(clientCountLabel);
        add(topPanel, BorderLayout.NORTH);

        // ── Center: αρχεία ───────────────────────────────────────────
        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Διαθέσιμα Αρχεία"));
        tableScroll.setPreferredSize(new Dimension(800, 250));

        // ── Bottom: logs ─────────────────────────────────────────────
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Logs"));
        logScroll.setPreferredSize(new Dimension(800, 200));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, tableScroll, logScroll
        );
        splitPane.setResizeWeight(0.6);
        add(splitPane, BorderLayout.CENTER);
    }

    private void setupActions() {
        // Start button
        startBtn.addActionListener(e -> startServer());

        // Stop button
        stopBtn.addActionListener(e -> stopServer());
    }

    private void startServer() {
        String videosFolder = "videos";

        server = new StreamingServer(Protocol.SERVER_PORT, videosFolder);

        // Τρέξε τον server σε ξεχωριστό thread για να μην παγώσει το GUI
        serverThread = new Thread(() -> {
            server.start();
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Περίμενε λίγο και ενημέρωσε το GUI
        Timer timer = new Timer(8000, evt -> {
            if (server.isRunning()) {
                updateStatus(true);
                populateFileTable();
                startClientCountUpdater();
            }
        });
        timer.setRepeats(false);
        timer.start();

        log("Server ξεκίνησε στο port " + Protocol.SERVER_PORT);
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            updateStatus(false);
            log("Server σταμάτησε.");
        }
    }

    private void updateStatus(boolean running) {
        SwingUtilities.invokeLater(() -> {
            startBtn.setEnabled(!running);
            stopBtn.setEnabled(running);
            if (running) {
                statusLabel.setText("● Σε λειτουργία");
                statusLabel.setForeground(new Color(0, 150, 0));
            } else {
                statusLabel.setText("● Εκτός λειτουργίας");
                statusLabel.setForeground(Color.RED);
            }
        });
    }

    private void populateFileTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            if (server.getAvailableFiles() != null) {
                server.getAvailableFiles().forEach(f ->
                        tableModel.addRow(new Object[]{
                                f.getName(), f.getFormat(), f.getResolution()
                        })
                );
            }
        });
    }

    /** Ανανεώνει τον αριθμό ενεργών clients κάθε 2 δευτερόλεπτα */
    private void startClientCountUpdater() {
        Timer timer = new Timer(2000, e -> {
            if (server != null && server.isRunning()) {
                SwingUtilities.invokeLater(() ->
                        clientCountLabel.setText(
                                "Ενεργοί Clients: " + server.getActiveClientCount()
                        )
                );
            }
        });
        timer.start();
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerGUI::new);
    }
}
