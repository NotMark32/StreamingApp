package com.streaming.common;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SSLConfig {

    private static final String KEYSTORE_PATH = "streaming.p12";
    private static final String KEYSTORE_PASS = "streaming123";
    private static final String KEYSTORE_TYPE = "PKCS12";

    //Δημιουργεί SSLContext για τον Server
    public static SSLContext createServerSSLContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, KEYSTORE_PASS.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keyStore, KEYSTORE_PASS.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }


    public static SSLContext createClientSSLContext() throws Exception {
        // Trust manager που αποδέχεται όλα τα certificates (για development)
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new java.security.SecureRandom());
        return sslContext;
    }


    //Δημιουργεί SSLServerSocket
    public static SSLServerSocket createSSLServerSocket(int port) throws Exception {
        SSLContext ctx = createServerSSLContext();
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        return (SSLServerSocket) factory.createServerSocket(port);
    }

    //Δημιουργεί SSLSocket για σύνδεση σε server
    public static SSLSocket createSSLSocket(String host, int port) throws Exception {
        SSLContext ctx = createClientSSLContext();
        SSLSocketFactory factory = ctx.getSocketFactory();
        return (SSLSocket) factory.createSocket(host, port);
    }
}