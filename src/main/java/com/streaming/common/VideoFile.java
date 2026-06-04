package com.streaming.common;

import java.io.Serializable;

public class VideoFile implements Serializable {

    private final String name;        // π.χ. "Forrest_Gump"
    private final String format;      // π.χ. "mkv"
    private final String resolution;  // π.χ. "480p"
    private final String filePath;    // full path στο δίσκο

    public VideoFile(String name, String format, String resolution, String filePath) {
        this.name       = name;
        this.format     = format;
        this.resolution = resolution;
        this.filePath   = filePath;
    }

    // Getters
    public String getName()       { return name; }
    public String getFormat()     { return format; }
    public String getResolution() { return resolution; }
    public String getFilePath()   { return filePath; }

    /**
     * Επιστρέφει το όνομα αρχείου όπως θα φαίνεται στον Client
     * π.χ. "Forrest_Gump-480p.mkv"
     */
    public String getFileName() {
        return name + "-" + resolution + "." + format;
    }

    @Override
    public String toString() {
        return getFileName();
    }
}