package com.streaming.server;

import com.streaming.common.Protocol;
import com.streaming.common.VideoFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final List<VideoFile> allFiles;
    private final StreamingServer server;

    private BufferedReader in;
    private PrintWriter out;
    private String clientAddress;

    private double clientSpeed  = 0;
    private String clientFormat = null;

    public ClientHandler(Socket socket, List<VideoFile> allFiles, StreamingServer server) {
        this.socket        = socket;
        this.allFiles      = allFiles;
        this.server        = server;
        this.clientAddress = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            logger.info("[{}] Σύνδεση εγκαθίσταται...", clientAddress);

            String message;
            while ((message = in.readLine()) != null) {
                logger.info("[{}] Έλαβα: '{}'", clientAddress, message);
                handleMessage(message);
            }

        } catch (IOException e) {
            logger.warn("[{}] Αποσύνδεση: {}", clientAddress, e.getMessage());
        } finally {
            disconnect();
            server.removeClient(this);
        }
    }

    private void handleMessage(String message) {
        if (message.startsWith(Protocol.SPEED_INFO)) {
            String[] parts = message.split("\\|");
            if (parts.length == 2) {
                clientSpeed = Double.parseDouble(parts[1]);
                logger.info("[{}] Ταχύτητα client: {} Mbps", clientAddress, clientSpeed);
            }

        } else if (message.startsWith(Protocol.REQUEST_FILE_LIST)) {
            String[] parts = message.split("\\|");
            clientFormat = (parts.length == 2) ? parts[1] : "mkv";
            sendFileList();

        } else if (message.startsWith(Protocol.REQUEST_FILE)) {
            String[] parts = message.split("\\|");
            if (parts.length == 3) {
                sendFile(parts[1], parts[2]);
            }

        } else if (message.equals(Protocol.DISCONNECT)) {
            logger.info("[{}] Ο client ζήτησε αποσύνδεση", clientAddress);
            disconnect();
        }
    }

    private void sendFileList() {
        if (clientFormat == null) clientFormat = "mkv";

        List<VideoFile> filtered = allFiles.stream()
                .filter(f -> f.getFormat().equals(clientFormat))
                .filter(f -> canClientPlay(f.getResolution()))
                .collect(Collectors.toList());

        String fileNames = filtered.stream()
                .map(VideoFile::getFileName)
                .collect(Collectors.joining(","));

        out.println("FILE_LIST|" + fileNames);
        logger.info("[{}] Εστάλη λίστα: {}", clientAddress, fileNames);
    }

    private void sendFile(String fileName, String protocol) {
        VideoFile file = allFiles.stream()
                .filter(f -> f.getFileName().equals(fileName))
                .findFirst()
                .orElse(null);

        if (file == null) {
            out.println(Protocol.RESPONSE_ERROR + "|Αρχείο δεν βρέθηκε");
            return;
        }

        try {
            String clientIP = socket.getInetAddress().getHostAddress();

            String destination = switch (protocol) {
                case Protocol.UDP -> "udp://" + clientIP + ":" + Protocol.UDP_RECV_PORT;
                case Protocol.RTP -> {
                    // Δημιούργησε SDP file
                    String sdpContent = "v=0\n" +
                            "o=- 0 0 IN IP4 " + clientIP + "\n" +
                            "s=Stream\n" +
                            "c=IN IP4 " + clientIP + "\n" +
                            "t=0 0\n" +
                            "m=video " + Protocol.RTP_PORT + " RTP/AVP 96\n" +
                            "a=rtpmap:96 H264/90000\n" +
                            "a=fmtp:96 packetization-mode=1\n";

                    java.nio.file.Files.writeString(
                            java.nio.file.Path.of("stream.sdp"), sdpContent
                    );
                    yield "rtp://" + clientIP + ":" + Protocol.RTP_PORT;
                }
                default -> "tcp://0.0.0.0:" + Protocol.UDP_PORT + "?listen=1";
            };

            String format = switch (protocol) {
                case Protocol.UDP -> "mpegts";
                case Protocol.RTP -> "rtp";
                default           -> "mpegts";
            };

            // Χτίσε εντολή FFMPEG
            List<String> cmd = new ArrayList<>();
            cmd.add("ffmpeg");
            cmd.add("-re");
            cmd.add("-i"); cmd.add(file.getFilePath());

            if (protocol.equals(Protocol.RTP)) {
                // RTP: re-encode με keyframes για σωστή αποκωδικοποίηση
                cmd.add("-c:v"); cmd.add("libx264");
                cmd.add("-preset"); cmd.add("ultrafast");
                cmd.add("-tune"); cmd.add("zerolatency");
                cmd.add("-x264opts"); cmd.add("keyint=30:min-keyint=30");
            } else {
                // TCP/UDP: copy για γρήγορη μετάδοση
                cmd.add("-c:v"); cmd.add("copy");
            }

            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-f"); cmd.add(format);
            cmd.add(destination);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            logger.info("[{}] FFMPEG ξεκίνησε → {} via {}", clientAddress, destination, protocol);

            // Στείλε OK στον Client
            out.println(Protocol.RESPONSE_OK + "|" + fileName + "|" + protocol);
            out.flush();

            // Για RTP στείλε και το SDP
            if (protocol.equals(Protocol.RTP)) {
                try {
                    Thread.sleep(100);
                    String sdp = java.nio.file.Files.readString(
                            java.nio.file.Path.of("stream.sdp")
                    );
                    out.println("SDP|" + sdp.replace("\n", "\\n"));
                    out.flush();
                } catch (Exception e) {
                    logger.error("Σφάλμα αποστολής SDP: {}", e.getMessage());
                }
            }

            process.getInputStream().transferTo(System.out);
            process.waitFor();
            process.destroy();
            Thread.sleep(2000);

            logger.info("[{}] Streaming ολοκληρώθηκε: {}", clientAddress, fileName);

        } catch (IOException | InterruptedException e) {
            logger.error("[{}] Σφάλμα streaming: {}", clientAddress, e.getMessage());
            out.println(Protocol.RESPONSE_ERROR + "|" + e.getMessage());
        }
    }

    private boolean canClientPlay(String resolution) {
        return switch (resolution) {
            case "240p"  -> clientSpeed >= 0.3;
            case "360p"  -> clientSpeed >= 0.75;
            case "480p"  -> clientSpeed >= 1.0;
            case "720p"  -> clientSpeed >= 2.5;
            case "1080p" -> clientSpeed >= 3.0;
            default      -> true;
        };
    }

    public void disconnect() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            logger.error("Σφάλμα κλεισίματος socket: {}", e.getMessage());
        }
    }
}