package com.streaming.gui;

import com.streaming.client.StreamingClient;
import com.streaming.common.Protocol;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ClientGUI extends JFrame {

    private StreamingClient client;

    // Components
    private JTextField serverHostField;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JLabel statusLabel;
    private JLabel speedLabel;
    private JComboBox<String> formatCombo;
    private JButton getListBtn;
    private JList<String> fileList;
    private DefaultListModel<String> listModel;
    private JComboBox<String> protocolCombo;
    private JButton playBtn;
    private JTextArea logArea;

    public ClientGUI() {
        super("Streaming Client");
        initComponents();
        layoutComponents();
        setupActions();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        serverHostField = new JTextField("localhost", 15);
        connectBtn      = new JButton("🔌 Σύνδεση");
        disconnectBtn   = new JButton("✖ Αποσύνδεση");
        statusLabel     = new JLabel("● Αποσυνδεδεμένος");
        speedLabel      = new JLabel("Ταχύτητα: -");
        formatCombo     = new JComboBox<>(new String[]{"mkv", "mp4", "avi"});
        getListBtn      = new JButton("📋 Λήψη Λίστας");
        listModel       = new DefaultListModel<>();
        fileList        = new JList<>(listModel);
        protocolCombo   = new JComboBox<>(new String[]{
                "Auto", Protocol.TCP, Protocol.UDP, Protocol.RTP
        });
        playBtn         = new JButton("▶ Αναπαραγωγή");
        logArea         = new JTextArea();

        // Αρχική κατάσταση
        disconnectBtn.setEnabled(false);
        getListBtn.setEnabled(false);
        playBtn.setEnabled(false);
        statusLabel.setForeground(Color.RED);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // ── Top: σύνδεση ─────────────────────────────────────────────
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        connectPanel.setBorder(BorderFactory.createTitledBorder("Σύνδεση"));
        connectPanel.add(new JLabel("Server:"));
        connectPanel.add(serverHostField);
        connectPanel.add(connectBtn);
        connectPanel.add(disconnectBtn);
        connectPanel.add(Box.createHorizontalStrut(15));
        connectPanel.add(statusLabel);
        connectPanel.add(Box.createHorizontalStrut(15));
        connectPanel.add(speedLabel);
        add(connectPanel, BorderLayout.NORTH);

        // ── Center: επιλογή αρχείου ──────────────────────────────────
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Επάνω μέρος: format + λήψη λίστας
        JPanel listControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        listControlPanel.add(new JLabel("Format:"));
        listControlPanel.add(formatCombo);
        listControlPanel.add(getListBtn);
        centerPanel.add(listControlPanel, BorderLayout.NORTH);

        // Λίστα αρχείων
        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setBorder(BorderFactory.createTitledBorder("Διαθέσιμα Αρχεία"));
        centerPanel.add(listScroll, BorderLayout.CENTER);

        // Κάτω μέρος: πρωτόκολλο + play
        JPanel playPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        playPanel.add(new JLabel("Πρωτόκολλο:"));
        playPanel.add(protocolCombo);
        playPanel.add(playBtn);
        centerPanel.add(playPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // ── Bottom: logs ─────────────────────────────────────────────
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Logs"));
        logScroll.setPreferredSize(new Dimension(700, 150));
        add(logScroll, BorderLayout.SOUTH);
    }

    private void setupActions() {

        // Σύνδεση
        connectBtn.addActionListener(e -> {
            String host = serverHostField.getText().trim();
            client = new StreamingClient(host, Protocol.SERVER_PORT);

            new SwingWorker<Boolean, Void>() {
                protected Boolean doInBackground() {
                    return client.connect();
                }
                protected void done() {
                    try {
                        if (get()) {
                            updateStatus(true);
                            log("Συνδέθηκε στον " + host);

                            // Speed test σε background
                            runSpeedTest();
                        } else {
                            log("Αδύνατη η σύνδεση στον " + host);
                        }
                    } catch (Exception ex) {
                        log("Σφάλμα: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // Αποσύνδεση
        disconnectBtn.addActionListener(e -> {
            if (client != null) {
                client.disconnect();
                updateStatus(false);
                listModel.clear();
                log("Αποσυνδέθηκε.");
            }
        });

        // Λήψη λίστας
        getListBtn.addActionListener(e -> {
            String format = (String) formatCombo.getSelectedItem();
            new SwingWorker<List<String>, Void>() {
                protected List<String> doInBackground() {
                    return client.requestFileList(format);
                }
                protected void done() {
                    try {
                        List<String> files = get();
                        listModel.clear();
                        files.forEach(listModel::addElement);
                        playBtn.setEnabled(!files.isEmpty());
                        log("Λήφθηκαν " + files.size() + " αρχεία για format: " + format);
                    } catch (Exception ex) {
                        log("Σφάλμα: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // Αναπαραγωγή
        playBtn.addActionListener(e -> {
            String selected = fileList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this,
                        "Επέλεξε ένα αρχείο από τη λίστα!", "Προσοχή",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String protocol = (String) protocolCombo.getSelectedItem();
            if ("Auto".equals(protocol)) protocol = null;

            final String finalProtocol = protocol;
            log("Ξεκινά streaming: " + selected);

            new SwingWorker<Boolean, Void>() {
                protected Boolean doInBackground() {
                    return client.requestFile(selected, finalProtocol);
                }
                protected void done() {
                    try {
                        if (get()) log("Streaming ολοκληρώθηκε: " + selected);
                        else       log("Αποτυχία streaming.");
                    } catch (Exception ex) {
                        log("Σφάλμα: " + ex.getMessage());
                    }
                }
            }.execute();
        });
    }

    private void runSpeedTest() {
        log("Speed test σε εξέλιξη...");
        new SwingWorker<Double, Void>() {
            protected Double doInBackground() {
                return client.runSpeedTest();
            }
            protected void done() {
                try {
                    double speed = get();
                    speedLabel.setText(String.format("Ταχύτητα: %.2f Mbps", speed));
                    getListBtn.setEnabled(true);
                    log(String.format("Speed test: %.2f Mbps", speed));
                } catch (Exception ex) {
                    log("Speed test σφάλμα: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void updateStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            connectBtn.setEnabled(!connected);
            disconnectBtn.setEnabled(connected);
            serverHostField.setEnabled(!connected);
            if (connected) {
                statusLabel.setText("● Συνδεδεμένος");
                statusLabel.setForeground(new Color(0, 150, 0));
            } else {
                statusLabel.setText("● Αποσυνδεδεμένος");
                statusLabel.setForeground(Color.RED);
                speedLabel.setText("Ταχύτητα: -");
                getListBtn.setEnabled(false);
                playBtn.setEnabled(false);
            }
        });
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
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}