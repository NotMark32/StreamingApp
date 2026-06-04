package com.streaming.client;

import com.streaming.common.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;

public class StreamingClient {

    private static final Logger logger = LoggerFactory.getLogger(StreamingClient.class);

    private final String serverHost;
    private final int serverPort;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private double speedMbps;
    private String selectedFormat;
    private List<String> availableFiles;

    public StreamingClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    /**
     * Βήμα 1: Σύνδεση στον server
     */
    public boolean connect() {
        try {
            logger.info("Σύνδεση στον server {}:{}...", serverHost, serverPort);
            socket = new Socket(serverHost, serverPort);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            logger.info("Συνδέθηκε επιτυχώς!");
            return true;
        } catch (IOException e) {
            logger.error("Αδύνατη η σύνδεση: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Βήμα 2: Speed test και αποστολή αποτελέσματος στον server
     */
    public double runSpeedTest() {
        SpeedTest speedTest = new SpeedTest();
        speedMbps = speedTest.measureSpeed();
        logger.info("Ταχύτητα: {} Mbps", speedMbps);

        // Στείλε την ταχύτητα στον server
        out.println(Protocol.SPEED_INFO + "|" + speedMbps);
        return speedMbps;
    }

    /**
     * Βήμα 3: Ζήτησε λίστα αρχείων για συγκεκριμένο format
     */
    public List<String> requestFileList(String format) {
        this.selectedFormat = format;
        out.println(Protocol.REQUEST_FILE_LIST + "|" + format);

        try {
            String response = in.readLine();
            logger.info("Server απάντηση: {}", response);

            // Αναμένουμε: FILE_LIST|αρχείο1,αρχείο2,...
            if (response != null && response.startsWith("FILE_LIST|")) {
                String filesPart = response.substring("FILE_LIST|".length());
                if (filesPart.isEmpty()) {
                    availableFiles = List.of();
                } else {
                    availableFiles = Arrays.asList(filesPart.split(","));
                }
                logger.info("Διαθέσιμα αρχεία: {}", availableFiles);
                return availableFiles;
            }
        } catch (IOException e) {
            logger.error("Σφάλμα λήψης λίστας: {}", e.getMessage());
        }

        return List.of();
    }

    /**
     * Βήμα 4: Ζήτησε streaming συγκεκριμένου αρχείου
     * Αν protocol == null, επιλέγεται αυτόματα βάσει ανάλυσης
     */
    public boolean requestFile(String fileName, String protocol) {
        if (protocol == null || protocol.isEmpty()) {
            String resolution = extractResolution(fileName);
            protocol = Protocol.autoSelectProtocol(resolution);
            logger.info("Auto-selected πρωτόκολλο: {} για {}", protocol, resolution);
        }

        out.println(Protocol.REQUEST_FILE + "|" + fileName + "|" + protocol);
        logger.info("Έστειλα στον Server: {}", Protocol.REQUEST_FILE + "|" + fileName + "|" + protocol);
        out.flush(); // σιγουρέψου ότι στάλθηκε
        try {
            String response = in.readLine();
            logger.info("Server απάντηση: {}", response);

            if (response != null && response.startsWith(Protocol.RESPONSE_OK)) {
                logger.info("Έναρξη λήψης: {}", fileName);

                // Περίμενε 1 δευτερόλεπτο για να ξεκινήσει ο Server
                Thread.sleep(4000);

                startReceiving(protocol);
                return true;
            } else {
                logger.error("Server error: {}", response);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Σφάλμα αίτησης αρχείου: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ξεκινά FFMPEG για λήψη του stream
     */
    private void startReceiving(String protocol) {
        String source = "tcp://127.0.0.1:" + Protocol.UDP_PORT;
        logger.info("Έναρξη αναπαραγωγής από: {}", source);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffplay",
                    "-i", source,
                    "-autoexit",
                    "-window_title", "Streaming Video",
                    "-infbuf",
                    "-fflags", "nobuffer"
            );

            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
            process.destroy();
            logger.info("ffplay process τερματίστηκε");
// Περίμενε λίγο πριν επιτραπεί νέο streaming
            Thread.sleep(1000);
            logger.info("Αναπαραγωγή ολοκληρώθηκε!");

        } catch (IOException | InterruptedException e) {
            logger.error("Σφάλμα αναπαραγωγής: {}", e.getMessage());
        }
    }

    /**
     * Αποσύνδεση από τον server
     */
    public void disconnect() {
        try {
            if (out != null) out.println(Protocol.DISCONNECT);
            if (socket != null && !socket.isClosed()) socket.close();
            logger.info("Αποσυνδέθηκε από τον server.");
        } catch (IOException e) {
            logger.error("Σφάλμα αποσύνδεσης: {}", e.getMessage());
        }
    }

    // ── Βοηθητικές μέθοδοι ──────────────────────────────────────────

    /** "Forrest_Gump-480p.mkv" → "480p" */
    private String extractResolution(String fileName) {
        int dash = fileName.lastIndexOf('-');
        int dot  = fileName.lastIndexOf('.');
        if (dash == -1 || dot == -1) return "480p";
        return fileName.substring(dash + 1, dot);
    }

    private String buildSource(String protocol) {
        return switch (protocol) {
            case Protocol.UDP -> "udp://0.0.0.0:" + Protocol.UDP_PORT;
            case Protocol.RTP -> "rtp://0.0.0.0:" + Protocol.RTP_PORT;
            default -> "tcp://127.0.0.1:" + Protocol.UDP_PORT + "?listen=1";
        };
    }

    // Getters για GUI
    public double getSpeedMbps()         { return speedMbps; }
    public List<String> getAvailableFiles() { return availableFiles; }
    public String getSelectedFormat()    { return selectedFormat; }

    // ── Main για να τρέξεις μόνο τον Client ─────────────────────────
    public static void main(String[] args) {
        StreamingClient client = new StreamingClient("localhost", Protocol.SERVER_PORT);

        if (client.connect()) {
            client.runSpeedTest();
            List<String> files = client.requestFileList("mkv");

            if (!files.isEmpty()) {
                client.requestFile(files.get(0), null); // auto-select protocol
            }

            client.disconnect();
        }
    }
}
