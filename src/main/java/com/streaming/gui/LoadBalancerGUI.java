package com.streaming.gui;

import com.streaming.server.LoadBalancer;
import com.streaming.common.Protocol;
import com.streaming.server.StreamingServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class LoadBalancerGUI extends JFrame {

    private LoadBalancer loadBalancer;
    private Thread lbThread;

    // Server instances
    private StreamingServer server1;
    private StreamingServer server2;

    // Components
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JTable serverTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;

    public LoadBalancerGUI() {
        super("Load Balancer");
        initComponents();
        layoutComponents();
        setupActions();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        startBtn    = new JButton("▶ Start");
        stopBtn     = new JButton("■ Stop");
        statusLabel = new JLabel("● Εκτός λειτουργίας");
        logArea     = new JTextArea();

        stopBtn.setEnabled(false);
        statusLabel.setForeground(Color.RED);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        String[] cols = {"Server", "Port", "Clients"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        serverTable = new JTable(tableModel);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // Top
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Load Balancer (Port 4999)"));
        topPanel.add(startBtn);
        topPanel.add(stopBtn);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(statusLabel);
        add(topPanel, BorderLayout.NORTH);

        // Center: server table
        JScrollPane tableScroll = new JScrollPane(serverTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Servers"));
        add(tableScroll, BorderLayout.CENTER);

        // Bottom: logs
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Logs"));
        logScroll.setPreferredSize(new Dimension(700, 180));
        add(logScroll, BorderLayout.SOUTH);
    }

    private void setupActions() {
        startBtn.addActionListener(e -> startAll());
        stopBtn.addActionListener(e -> stopAll());
    }

    private void startAll() {
        // Ξεκίνα Server 1 στο port 5000
        server1 = new StreamingServer(Protocol.SERVER_PORT, "videos");
        new Thread(server1::start).start();
        log("Server 1 ξεκίνησε στο port " + Protocol.SERVER_PORT);

        // Ξεκίνα Server 2 στο port 5010
        server2 = new StreamingServer(5010, "videos");
        new Thread(server2::start).start();
        log("Server 2 ξεκίνησε στο port 5010");

        // Ξεκίνα Load Balancer
        loadBalancer = new LoadBalancer();
        loadBalancer.addServer("localhost", Protocol.SERVER_PORT);
        loadBalancer.addServer("localhost", 5010);

        lbThread = new Thread(loadBalancer::start);
        lbThread.setDaemon(true);
        lbThread.start();
        log("Load Balancer ξεκίνησε στο port 4999");

        // Ενημέρωσε UI
        Timer timer = new Timer(3000, evt -> {
            updateStatus(true);
            populateTable();
            startTableUpdater();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void stopAll() {
        if (loadBalancer != null) loadBalancer.stop();
        if (server1 != null) server1.stop();
        if (server2 != null) server2.stop();
        updateStatus(false);
        log("Όλα σταμάτησαν.");
    }

    private void populateTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (LoadBalancer.ServerNode s : loadBalancer.getServers()) {
                tableModel.addRow(new Object[]{s.host, s.port, s.clientCount});
            }
        });
    }

    private void startTableUpdater() {
        new Timer(2000, e -> {
            if (loadBalancer != null && loadBalancer.isRunning()) {
                SwingUtilities.invokeLater(() -> {
                    for (int i = 0; i < loadBalancer.getServers().size(); i++) {
                        tableModel.setValueAt(
                                loadBalancer.getServers().get(i).clientCount, i, 2
                        );
                    }
                });
            }
        }).start();
    }

    private void updateStatus(boolean running) {
        SwingUtilities.invokeLater(() -> {
            startBtn.setEnabled(!running);
            stopBtn.setEnabled(running);
            statusLabel.setText(running ? "● Σε λειτουργία" : "● Εκτός λειτουργίας");
            statusLabel.setForeground(running ? new Color(0, 150, 0) : Color.RED);
        });
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoadBalancerGUI::new);
    }
}