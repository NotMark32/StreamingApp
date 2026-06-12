package com.streaming.server;

import com.streaming.common.VideoFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoConverter {

    private static final Logger logger = LoggerFactory.getLogger(VideoConverter.class);

    // Όλα τα formats και αναλύσεις που πρέπει να υπάρχουν
    private static final String[] FORMATS     = {"mp4", "avi", "mkv"};
    private static final String[] RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};

    // Αντιστοίχιση ανάλυσης
    private static final int[][] SIZES = {
            {426,  240},   // 240p
            {640,  360},   // 360p
            {854,  480},   // 480p
            {1280, 720},   // 720p
            {1920, 1080}   // 1080p
    };

    private final String videosFolder;

    public VideoConverter(String videosFolder) {
        this.videosFolder = videosFolder;
    }

    // Σκανάρει τον φάκελο βρίσκει όλες τις ταινίες και δημιουργεί τις εκδόσεις που λείπουν -> Επιστρέφει λίστα με όλα τα διαθέσιμα VideoFile αντικείμενα
    public List<VideoFile> processVideosFolder() {
        List<VideoFile> allFiles = new ArrayList<>();
        File folder = new File(videosFolder);

        if (!folder.exists()) {
            folder.mkdirs();
            logger.warn("Ο φάκελος videos/ δεν υπήρχε. Δημιουργήθηκε: {}", videosFolder);
            return allFiles;
        }

        // Βρες τα αρχικά αρχεία
        List<String> movieNames = findOriginalMovieNames(folder);
        logger.info("Βρέθηκαν {} ταινίες: {}", movieNames.size(), movieNames);

        // Για κάθε ταινία δημιούργησε όλα τις αναλύσεις που λείπουν
        for (String movieName : movieNames) {
            String originalFile = findOriginalFile(folder, movieName);
            if (originalFile == null) continue;

            String originalResolution = detectResolution(originalFile);

            for (int i = 0; i < RESOLUTIONS.length; i++) {
                String res = RESOLUTIONS[i];

                // Δεν μπορούμε να φτιάξουμε μεγαλύτερη ανάλυση από την αρχική
                if (resolutionIndex(res) > resolutionIndex(originalResolution)) {
                    logger.info("Παράλειψη {}-{}: μεγαλύτερη από την αρχική ({})",
                            movieName, res, originalResolution);
                    continue;
                }

                for (String format : FORMATS) {
                    String outputName = movieName + "-" + res + "." + format;
                    String outputPath = videosFolder + File.separator + outputName;
                    File outputFile  = new File(outputPath);

                    if (outputFile.exists()) {
                        logger.info("Υπάρχει ήδη: {}", outputName);
                    } else {
                        logger.info("Δημιουργία: {}", outputName);
                        boolean success = convertVideo(
                                originalFile, outputPath,
                                SIZES[i][0], SIZES[i][1]
                        );
                        if (!success) continue;
                    }

                    allFiles.add(new VideoFile(movieName, format, res, outputPath));
                }
            }
        }

        logger.info("Σύνολο διαθέσιμων αρχείων: {}", allFiles.size());
        return allFiles;
    }

    //Βρίσκει τα μοναδικά ονόματα ταινιών στον φάκελο
    private List<String> findOriginalMovieNames(File folder) {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return names;

        for (File f : files) {
            String fname = f.getName();
            // Αγνόησε αρχεία που δεν είναι video
            if (!isVideoFile(fname)) continue;

            // Αφαίρεσε το "-<ανάλυση>.<format>" για να πάρεις το όνομα
            String nameOnly = extractMovieName(fname);
            if (nameOnly != null && !names.contains(nameOnly)) {
                names.add(nameOnly);
            }
        }
        return names;
    }

    //Βρίσκει το αρχικό αρχείο μιας ταινίας (αυτό με τη μεγαλύτερη ανάλυση)
    private String findOriginalFile(File folder, String movieName) {
        String bestFile = null;
        int bestResIndex = -1;

        File[] files = folder.listFiles();
        if (files == null) return null;

        for (File f : files) {
            String fname = f.getName();
            if (!fname.startsWith(movieName)) continue;
            if (!isVideoFile(fname)) continue;

            String res = extractResolution(fname);
            if (res == null) continue;

            int idx = resolutionIndex(res);
            if (idx > bestResIndex) {
                bestResIndex = idx;
                bestFile = f.getAbsolutePath();
            }
        }
        return bestFile;
    }

     //Καλεί το FFMPEG για να μετατρέψει το αρχείο
    private boolean convertVideo(String inputPath, String outputPath, int width, int height) {
        try {
            // Χτίζουμε την εντολή FFMPEG:
            // ffmpeg -i input.mkv -vf scale=640:360 -c:a copy output.mp4
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", inputPath,
                    "-vf", "scale=" + width + ":" + height,
                    "-c:a", "copy",
                    "-y",           // overwrite χωρίς ερώτηση
                    outputPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Διάβασε output για να μην κολλήσει
            process.getInputStream().transferTo(System.out);

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Επιτυχής μετατροπή: {}", outputPath);
                return true;
            } else {
                logger.error("Αποτυχία μετατροπής: {} (exit code {})", outputPath, exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Σφάλμα κατά τη μετατροπή: {}", e.getMessage());
            return false;
        }
    }

    // Βοηθητικές μέθοδοι

    private boolean isVideoFile(String name) {
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv");
    }

    private String extractMovieName(String filename) {
        int dash = filename.lastIndexOf('-');
        if (dash == -1) return null;
        return filename.substring(0, dash);
    }

    private String extractResolution(String filename) {
        int dash = filename.lastIndexOf('-');
        int dot  = filename.lastIndexOf('.');
        if (dash == -1 || dot == -1 || dot <= dash) return null;
        return filename.substring(dash + 1, dot);
    }

    private String detectResolution(String filePath) {
        // Απλοποίηση: διαβάζουμε από το όνομα αρχείου
        String fname = new File(filePath).getName();
        String res = extractResolution(fname);
        return (res != null) ? res : "480p"; // default
    }

    private int resolutionIndex(String resolution) {
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            if (RESOLUTIONS[i].equals(resolution)) return i;
        }
        return -1;
    }
}