package com.streaming.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;

public class SpeedTest {

    private static final Logger logger = LoggerFactory.getLogger(SpeedTest.class);

    // Χρησιμοποιούμε ένα μικρό public test file για το speed test
    private static final String TEST_URL =
            "http://speedtest.tele2.net/1MB.zip";

    private static final int TEST_DURATION_MS = 5000; // 5 δευτερόλεπτα

    /**
     * Μετράει την ταχύτητα σύνδεσης του client σε Mbps.
     * Κατεβάζει δεδομένα για 5 δευτερόλεπτα και υπολογίζει.
     */
    public double measureSpeed() {
        logger.info("Ξεκινά speed test ({} δευτερόλεπτα)...", TEST_DURATION_MS / 1000);

        long startTime  = System.currentTimeMillis();
        long totalBytes = 0;

        try {
            URL url = new URL(TEST_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.connect();

            try (InputStream is = conn.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while (System.currentTimeMillis() - startTime < TEST_DURATION_MS) {
                    bytesRead = is.read(buffer);
                    if (bytesRead == -1) break;
                    totalBytes += bytesRead;
                }
            }

            conn.disconnect();

        } catch (IOException e) {
            logger.warn("Speed test απέτυχε: {}. Χρήση default 2 Mbps.", e.getMessage());
            return 2.0; // default τιμή αν αποτύχει
        }

        long elapsedMs  = System.currentTimeMillis() - startTime;
        double seconds  = elapsedMs / 1000.0;
        double megabits = (totalBytes * 8.0) / (1024 * 1024);
        double speedMbps = megabits / seconds;

        logger.info("Speed test αποτέλεσμα: {:.2f} Mbps ({} bytes σε {} ms)",
                speedMbps, totalBytes, elapsedMs);

        return Math.round(speedMbps * 100.0) / 100.0; // στρογγυλοποίηση
    }
}