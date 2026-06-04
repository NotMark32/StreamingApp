package com.streaming.server;

import com.streaming.common.Protocol;
import com.streaming.common.VideoFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class StreamingServer {

    private static final Logger logger = LoggerFactory.getLogger(StreamingServer.class);

    private final int port;
    private final String videosFolder;
    private List<VideoFile> availableFiles;
    private ServerSocket serverSocket;
    private boolean running = false;

    // Κρατάμε λίστα με ενεργούς clients (thread-safe)
    private final CopyOnWriteArrayList<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    public StreamingServer(int port, String videosFolder) {
        this.port         = port;
        this.videosFolder = videosFolder;
    }

    /**
     * Εκκίνηση Server:
     * 1. Τρέχει VideoConverter για να δημιουργήσει αρχεία
     * 2. Ανοίγει ServerSocket
     * 3. Περιμένει clients σε loop
     */
    public void start() {
        logger.info("=== Streaming Server ξεκινάει ===");

        // Βήμα 1: Επεξεργασία αρχείων με FFMPEG
        logger.info("Επεξεργασία αρχείων βίντεο...");
        VideoConverter converter = new VideoConverter(videosFolder);
        availableFiles = converter.processVideosFolder();
        logger.info("Έτοιμα {} αρχεία για streaming", availableFiles.size());

        // Βήμα 2: Άνοιγμα ServerSocket
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("Server ακούει στο port {}", port);

            // Βήμα 3: Loop αποδοχής clients
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Νέος Client συνδέθηκε: {}",
                            clientSocket.getInetAddress().getHostAddress());

                    // Ξεκίνα νέο thread για τον client
                    ClientHandler handler = new ClientHandler(
                            clientSocket, availableFiles, this
                    );
                    activeClients.add(handler);
                    new Thread(handler).start();

                } catch (SocketException e) {
                    if (running) logger.error("Socket error: {}", e.getMessage());
                }
            }

        } catch (IOException e) {
            logger.error("Αδύνατη η εκκίνηση server: {}", e.getMessage());
        }
    }

    /**
     * Διακοπή Server
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            // Κλείσε όλους τους ενεργούς clients
            for (ClientHandler client : activeClients) {
                client.disconnect();
            }
            logger.info("Server σταμάτησε.");
        } catch (IOException e) {
            logger.error("Σφάλμα κατά τη διακοπή: {}", e.getMessage());
        }
    }

    /**
     * Αφαίρεση client από τη λίστα όταν αποσυνδεθεί
     */
    public void removeClient(ClientHandler client) {
        activeClients.remove(client);
        logger.info("Client αφαιρέθηκε. Ενεργοί clients: {}", activeClients.size());
    }

    // Getters για το GUI
    public int getActiveClientCount() { return activeClients.size(); }
    public List<VideoFile> getAvailableFiles() { return availableFiles; }
    public boolean isRunning() { return running; }

    // ── Main για να τρέξεις μόνο τον Server ──────────────────────────
    public static void main(String[] args) {
        String videosFolder = "videos"; // φάκελος στο working directory
        StreamingServer server = new StreamingServer(Protocol.SERVER_PORT, videosFolder);
        server.start();
    }
}