package cn.sparkpixel.io.network;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModOutputStream extends OutputStream {
    private final Path tempFile;
    private final Path destination;
    private final String expectedSha1;
    private final OutputStream out;
    private final MessageDigest digest;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private static final int BUFFER_SIZE = 8192 * 8;

    public ModOutputStream(Path destination, String expectedSha1) throws IOException {
        this.destination = destination;
        this.expectedSha1 = expectedSha1;
        Path tempFile = null;
        OutputStream out = null;
        try {
            tempFile = Files.createTempFile(destination.getParent(),
                    "mod_" + destination.getFileName() + "_", ".tmp");
            out = new BufferedOutputStream(Files.newOutputStream(tempFile), BUFFER_SIZE);
            this.digest = MessageDigest.getInstance("SHA-1");
            this.tempFile = tempFile;
            this.out = out;
        } catch (Exception e) {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
            throw new IOException("Failed to create download stream: " + destination.getFileName(), e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        checkClosed();
        try {
            out.write(b);
            digest.update((byte) b);
        } catch (IOException e) {
            closeAndThrow(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();
        try {
            out.write(b, off, len);
            digest.update(b, off, len);
        } catch (IOException e) {
            closeAndThrow(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.getAndSet(true)) {
            return;
        }

        IOException exception = null;
        try {
            out.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            if (exception == null) {
                String actualSha1 = bytesToHex(digest.digest());
                if (actualSha1.equalsIgnoreCase(expectedSha1)) {
                    try {
                        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
                        return;
                    } catch (IOException e) {
                        exception = new IOException("Failed to move files: " + destination.getFileName(), e);
                    }
                } else {
                    exception = new IOException("Failed to verify checksum: " + destination.getFileName());
                }
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                }
            }
        }
        throw exception;
    }

    private void checkClosed() throws IOException {
        if (closed.get()) {
            throw new IOException("Stream Closed");
        }
    }

    private void closeAndThrow(IOException e) throws IOException {
        try {
            close();
        } catch (IOException closeEx) {
            e.addSuppressed(closeEx);
        }
        throw e;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}