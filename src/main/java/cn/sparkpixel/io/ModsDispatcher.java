package cn.sparkpixel.io;

import cn.sparkpixel.gui.GlProgressScreen;
import cn.sparkpixel.io.network.DownloadDispatcher;
import cn.sparkpixel.io.network.DownloadTask;
import cn.sparkpixel.io.network.ModOutputStream;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public class ModsDispatcher {
    private final Path modsDir;
    private final String baseUrl;
    private static final int BUFFER_SIZE = 8192 * 8;

    private static class ModItem {
        public String file_name;
        public String sha1;
        public String path;
    }

    public ModsDispatcher(Path modsDir, String baseUrl) {
        this.modsDir = modsDir;
        this.baseUrl = baseUrl;
    }

    public boolean runSync(String jsonContent, GlProgressScreen display) throws Exception {
        Files.createDirectories(modsDir);
        display.printLog("Synchronizing Mods...");

        Gson gson = new Gson();
        ModItem[] modItems = gson.fromJson(jsonContent, ModItem[].class);

        Set<String> existingFiles = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
            for (Path path : stream) {
                existingFiles.add(path.getFileName().toString());
            }
        }

        Set<String> requiredFiles = new HashSet<>();
        DownloadDispatcher downloadDispatcher = new DownloadDispatcher(display);
        int completedCount = 0;
        for (ModItem item : modItems) {
            String fileName = item.file_name;
            requiredFiles.add(fileName);
            Path modFile = modsDir.resolve(fileName);
            if (!Files.exists(modFile) || !verifyFileSha1(modFile, item.sha1)) {
                String encodedPath = item.path.replace(" ", "%20")
                        .replace("[", "%5B")
                        .replace("]", "%5D")
                        .replace("+", "%2B");
                String fullUrl = baseUrl + "/" + encodedPath;

                DownloadTask task = new DownloadTask(downloadDispatcher,
                        fullUrl, fileName, -1);
                try {
                    downloadDispatcher.dispatch(task, () -> {
                        try {
                            return new ModOutputStream(modFile, item.sha1);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    throw new IOException("Failed to create download task: " + fileName, e);
                }
            } else {
                completedCount++;
                display.setInfo(fileName, ": Existing and valid");
                display.setProgress((float) completedCount / modItems.length, 0);
            }
        }

        try {
            while (!downloadDispatcher.tasksFinished()) {
                downloadDispatcher.updateSummary();
                display.redrawScreen(true);
                try {
                    Thread.sleep(1000 / 30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download was terminated", e);
                }
            }
        } finally {
            downloadDispatcher.close();
        }

        existingFiles.removeAll(requiredFiles);
        for (String fileToDelete : existingFiles) {
            display.printLog("Deleting unusable mods: " + fileToDelete);
            Files.delete(modsDir.resolve(fileToDelete));
        }

        display.printLog("Mods Synchronization completed.");
        return true;
    }

    private boolean verifyFileSha1(Path file, String expectedSha1) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            String actualSha1 = bytesToHex(digest.digest());
            return actualSha1.equalsIgnoreCase(expectedSha1);
        } catch (Exception e) {
            throw new IOException("Failed to verify checksum", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}