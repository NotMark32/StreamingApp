package com.streaming.client;

import com.streaming.common.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.streaming.server.LoadBalancer;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
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

    public double runSpeedTest() {
        SpeedTest speedTest = new SpeedTest();
        speedMbps = speedTest.measureSpeed();
        logger.info("Ταχύτητα: {} Mbps", speedMbps);
        out.println(Protocol.SPEED_INFO + "|" + speedMbps);
        return speedMbps;
    }

    public List<String> requestFileList(String format) {
        this.selectedFormat = format;
        out.println(Protocol.REQUEST_FILE_LIST + "|" + format);

        try {
            String response = in.readLine();
            logger.info("Server απάντηση: {}", response);

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

    public boolean requestFile(String fileName, String protocol) {
        if (protocol == null || protocol.isEmpty()) {
            String resolution = extractResolution(fileName);
            protocol = Protocol.autoSelectProtocol(resolution);
            logger.info("Auto-selected πρωτόκολλο: {} για {}", protocol, resolution);
        }

        final String finalProtocol = protocol;

        // Για UDP/RTP: ξεκίνα το ffplay ΠΡΩΤΑ, μετά ζήτα το αρχείο
        if (protocol.equals(Protocol.UDP) || protocol.equals(Protocol.RTP)) {

            // Για RTP: ΜΗΝ ξεκινάς ffplay ακόμα
            if (protocol.equals(Protocol.UDP)) {
                new Thread(() -> startReceiving(finalProtocol)).start();
                try { Thread.sleep(1000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }

            out.println(Protocol.REQUEST_FILE + "|" + fileName + "|" + protocol);
            logger.info("Έστειλα στον Server: {}", Protocol.REQUEST_FILE + "|" + fileName + "|" + protocol);

            try {
                String response = in.readLine();
                logger.info("Server απάντηση: {}", response);

                if (response != null && response.startsWith(Protocol.RESPONSE_OK)) {

                    // Για RTP: διάβασε SDP και μετά ξεκίνα ffplay
                    if (finalProtocol.equals(Protocol.RTP)) {
                        String sdpLine = in.readLine();
                        if (sdpLine != null && sdpLine.startsWith("SDP|")) {
                            String sdpContent = sdpLine.substring(4).replace("\\n", "\n");
                            java.nio.file.Files.writeString(
                                    java.nio.file.Path.of("stream.sdp"), sdpContent
                            );
                            logger.info("SDP αποθηκεύτηκε, ξεκινά ffplay...");
                        }
                        // Τώρα ξεκίνα ffplay
                        new Thread(() -> startReceiving(finalProtocol)).start();
                    }
                    return true;
                } else {
                    logger.error("Server error: {}", response);
                    return false;
                }
            } catch (IOException e) {
                logger.error("Σφάλμα: {}", e.getMessage());
                return false;
            }

            // Για TCP: ο Server ξεκινάει πρώτα
        } else {
            out.println(Protocol.REQUEST_FILE + "|" + fileName + "|" + protocol);
            logger.info("Έστειλα στον Server: {}", Protocol.REQUEST_FILE + "|" + fileName + "|" + protocol);

            try {
                String response = in.readLine();
                logger.info("Server απάντηση: {}", response);

                if (response != null && response.startsWith(Protocol.RESPONSE_OK)) {
                    logger.info("Έναρξη λήψης: {}", fileName);
                    Thread.sleep(4000);
                    startReceiving(protocol);
                    return true;
                } else {
                    logger.error("Server error: {}", response);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Σφάλμα: {}", e.getMessage());
                return false;
            }
        }
    }

    private void startReceiving(String protocol) {
        String source = switch (protocol) {
            case Protocol.UDP -> "udp://0.0.0.0:" + Protocol.UDP_RECV_PORT;
            case Protocol.RTP -> "stream.sdp";
            default           -> "tcp://127.0.0.1:" + Protocol.UDP_PORT;
        };

        logger.info("Έναρξη αναπαραγωγής από: {} via {}", source, protocol);

        try {
            List<String> command = new ArrayList<>();
            command.add("ffplay");

            if (protocol.equals(Protocol.RTP)) {
                command.add("-protocol_whitelist");
                command.add("file,rtp,udp");
            }

            command.add("-i");
            command.add(source);
            command.add("-autoexit");
            command.add("-window_title");
            command.add("Streaming - " + protocol);
            command.add("-infbuf");
            command.add("-fflags");
            command.add("nobuffer");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
            process.destroy();
            logger.info("Αναπαραγωγή ολοκληρώθηκε!");

        } catch (IOException | InterruptedException e) {
            logger.error("Σφάλμα αναπαραγωγής: {}", e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (out != null) out.println(Protocol.DISCONNECT);
            if (socket != null && !socket.isClosed()) socket.close();
            logger.info("Αποσυνδέθηκε από τον server.");
        } catch (IOException e) {
            logger.error("Σφάλμα αποσύνδεσης: {}", e.getMessage());
        }
    }

    private String extractResolution(String fileName) {
        int dash = fileName.lastIndexOf('-');
        int dot  = fileName.lastIndexOf('.');
        if (dash == -1 || dot == -1) return "480p";
        return fileName.substring(dash + 1, dot);
    }

    public double getSpeedMbps()            { return speedMbps; }
    public List<String> getAvailableFiles() { return availableFiles; }
    public String getSelectedFormat()       { return selectedFormat; }

    public static void main(String[] args) {
        StreamingClient client = new StreamingClient("localhost", LoadBalancer.LB_PORT);
        if (client.connect()) {
            client.runSpeedTest();
            List<String> files = client.requestFileList("mkv");
            if (!files.isEmpty()) {
                client.requestFile(files.get(0), null);
            }
            client.disconnect();
        }
    }
}