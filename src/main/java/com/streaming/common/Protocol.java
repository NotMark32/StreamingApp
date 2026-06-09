package com.streaming.common;

public class Protocol {

    // Πρωτόκολλα μετάδοσης
    public static final String TCP = "TCP";
    public static final String UDP = "UDP";
    public static final String RTP = "RTP/UDP";

    // Μηνύματα επικοινωνίας Client-Server
    public static final String REQUEST_FILE_LIST = "GET_FILE_LIST";
    public static final String REQUEST_FILE      = "GET_FILE";
    public static final String RESPONSE_OK       = "OK";
    public static final String RESPONSE_ERROR    = "ERROR";
    public static final String DISCONNECT        = "DISCONNECT";
    public static final String SPEED_INFO        = "SPEED_INFO";

    // Ports
    public static final int SERVER_PORT   = 5000;
    public static final int UDP_PORT      = 5001;
    public static final int UDP_RECV_PORT = 5003;
    public static final int RTP_PORT      = 5002;

    /**
     * Επιλέγει αυτόματα πρωτόκολλο βάσει ανάλυσης
     * 240p, 360p   -> TCP
     * 480p         -> UDP
     * 720p, 1080p  -> RTP/UDP
     */
    public static String autoSelectProtocol(String resolution) {
        return switch (resolution) {
            case "240p", "360p"  -> TCP;
            case "480p"          -> UDP;
            case "720p", "1080p" -> RTP;
            default              -> TCP;
        };
    }
}