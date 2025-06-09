package github.qingha.io.network;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModOutputStream extends OutputStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModOutputStream.class);
    private final Path tempFile;
    private final Path destination;
    private final String expectedSha1;
    private final OutputStream out;
    private final MessageDigest digest;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean retainedForLater = false;

    public ModOutputStream(Path destination, String expectedSha1) throws IOException {
        this.destination = destination;
        this.expectedSha1 = expectedSha1;
        Path tempFile = null;
        OutputStream out = null;
        try {
            tempFile = Files.createTempFile(destination.getParent(),
                    "mod_" + destination.getFileName() + "_", ".tmp");
            out = new BufferedOutputStream(Files.newOutputStream(tempFile));
            this.digest = MessageDigest.getInstance("SHA-1");
            this.tempFile = tempFile;
            this.out = out;
        } catch (Exception e) {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
            if (tempFile != null) try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            throw new IOException("创建下载流失败: " + destination.getFileName(), e);
        }
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
        checkClosed();
        try {
            out.write(b, off, len);
            digest.update(b, off, len);
        } catch (IOException e) {
            closeAndThrow(e);
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
    public void close() throws IOException {
        if (closed.getAndSet(true)) return;

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
                        if (isFileLocked(e)) {
                            LOGGER.info("文件被占用，计划延迟移动: {}", destination.getFileName());
                            destination.toFile().deleteOnExit();
                            tempFile.toFile().deleteOnExit();
                            retainedForLater = true;
                            return;
                        }
                        exception = new IOException("移动文件失败: " + destination.getFileName(), e);
                    }
                } else {
                    exception = new IOException("校验和验证失败: " + destination.getFileName());
                }
            }
        } finally {
            if (!retainedForLater) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    if (exception == null) exception = e;
                }
            }
        }
        throw exception;
    }

    private void checkClosed() throws IOException {
        if (closed.get()) throw new IOException("流已关闭");
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
        for (byte b : bytes) result.append(String.format("%02x", b));
        return result.toString();
    }

    private boolean isFileLocked(IOException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("文件正被另一个进程使用");
    }
}