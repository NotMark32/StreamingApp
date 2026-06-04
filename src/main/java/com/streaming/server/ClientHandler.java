package com.streaming.server;

import com.streaming.common.Protocol;
import com.streaming.common.VideoFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
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
            // Πρώτα ξεκίνα το FFMPEG listener
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-re",
                    "-i", file.getFilePath(),
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-f", "mpegts",
                    "tcp://0.0.0.0:" + Protocol.UDP_PORT + "?listen=1&listen_timeout=15000000"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            logger.info("[{}] FFMPEG ξεκίνησε, περιμένει client στο port {}",
                    clientAddress, Protocol.UDP_PORT);

            // Τώρα πες στον Client να συνδεθεί
            out.println(Protocol.RESPONSE_OK + "|" + fileName + "|" + protocol);
            out.flush();

            process.getInputStream().transferTo(System.out);
            logger.info("[{}] Streaming ολοκληρώθηκε: {}", clientAddress, fileName);

            process.destroy();
            logger.info("[{}] FFMPEG process τερματίστηκε", clientAddress);

        } catch (IOException e) {
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