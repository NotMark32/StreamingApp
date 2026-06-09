package com.streaming.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    public static final int LB_PORT = 4999;

    private final List<ServerNode> servers = new ArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private ServerSocket lbSocket;
    private boolean running = false;

    /**
     * Προσθήκη Server node στον Load Balancer
     */
    public void addServer(String host, int port) {
        servers.add(new ServerNode(host, port));
        logger.info("Server προστέθηκε: {}:{}", host, port);
    }

    /**
     * Εκκίνηση Load Balancer
     */
    public void start() {
        if (servers.isEmpty()) {
            logger.error("Δεν υπάρχουν servers!");
            return;
        }

        try {
            lbSocket = new ServerSocket(LB_PORT);
            running = true;
            logger.info("Load Balancer ξεκίνησε στο port {}", LB_PORT);
            logger.info("Διαθέσιμοι servers: {}", servers.size());

            while (running) {
                try {
                    Socket clientSocket = lbSocket.accept();
                    logger.info("Νέος Client: {}",
                            clientSocket.getInetAddress().getHostAddress());

                    // Επέλεξε server με Round Robin
                    ServerNode target = selectServer();
                    logger.info("Ανακατεύθυνση → {}:{}", target.host, target.port);

                    // Ξεκίνα proxy thread
                    new Thread(new ProxyHandler(clientSocket, target)).start();

                } catch (SocketException e) {
                    if (running) logger.error("Socket error: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Load Balancer error: {}", e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (lbSocket != null) lbSocket.close();
        } catch (IOException e) {
            logger.error("Σφάλμα διακοπής: {}", e.getMessage());
        }
    }

    /**
     * Round Robin επιλογή server
     */
    private ServerNode selectServer() {
        int idx = roundRobinIndex.getAndIncrement() % servers.size();
        return servers.get(idx);
    }

    public List<ServerNode> getServers() { return servers; }
    public boolean isRunning()           { return running; }

    // ── ServerNode ───────────────────────────────────────────────────
    public static class ServerNode {
        public final String host;
        public final int port;
        public int clientCount = 0;

        public ServerNode(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() { return host + ":" + port; }
    }

    // ── ProxyHandler: κάνει proxy την επικοινωνία Client ↔ Server ───
    private static class ProxyHandler implements Runnable {

        private final Socket clientSocket;
        private final ServerNode target;

        public ProxyHandler(Socket clientSocket, ServerNode target) {
            this.clientSocket = clientSocket;
            this.target       = target;
        }

        @Override
        public void run() {
            try (Socket serverSocket = new Socket(target.host, target.port)) {
                target.clientCount++;

                // Bidirectional proxy
                Thread t1 = new Thread(() -> pipe(
                        clientSocket, serverSocket, "Client→Server"
                ));
                Thread t2 = new Thread(() -> pipe(
                        serverSocket, clientSocket, "Server→Client"
                ));

                t1.start();
                t2.start();
                t1.join();
                t2.join();

            } catch (IOException | InterruptedException e) {
                LoggerFactory.getLogger(ProxyHandler.class)
                        .warn("Proxy error: {}", e.getMessage());
            } finally {
                target.clientCount--;
                try { clientSocket.close(); } catch (IOException ignored) {}
            }
        }

        private void pipe(Socket from, Socket to, String direction) {
            try {
                InputStream  is = from.getInputStream();
                OutputStream os = to.getOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    os.flush();
                }
            } catch (IOException e) {
                // Φυσιολογικό όταν κλείσει η σύνδεση
            }
        }
    }

    public static void main(String[] args) {
        LoadBalancer lb = new LoadBalancer();
        lb.addServer("localhost", 5000); // Server 1
        lb.addServer("localhost", 5010); // Server 2
        lb.start();
    }
}
