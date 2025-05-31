package cn.sparkpixel.drm;

import org.apache.commons.io.IOUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class AssetEncryption {

    private static final byte[] HEADER_MAGIC = "RUU-PROTECT-2025".getBytes(StandardCharsets.UTF_8);

    public static InputStream wrapInputStream(FileInputStream fis) throws IOException {
        String filePath = fis.getChannel().toString();
        try {
            boolean isEncrypted = isEncrypted(fis);
            if (!isEncrypted) {
                return new BufferedInputStream(fis);
            }
            byte[] decryptedContent = decryptStream(fis);
            fis.close();
            return new BufferedInputStream(new ByteArrayInputStream(decryptedContent));
        } catch (Exception ex) {
            ex.printStackTrace();
            fis.close();
            throw new IOException("Failed to process file: " + filePath, ex);
        }
    }

    private static byte[] decryptStream(FileInputStream fis) throws Exception {
        fis.getChannel().position(0);
        DataInputStream dis = new DataInputStream(new BufferedInputStream(fis));
        dis.skipBytes(HEADER_MAGIC.length);
        int versionMajor = dis.readInt();
        int versionMinor = dis.readInt();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] key = dis.readNBytes(32);
        SecretKeySpec aesKey = new SecretKeySpec(key, "AES");
        byte[] iv = Arrays.copyOfRange(sha256.digest(key), 0, 16);
        IvParameterSpec aesIv = new IvParameterSpec(iv);
        int len = dis.readInt();
        if (len <= 0 || len > fis.getChannel().size()) {
            throw new IOException("Invalid content length: " + len);
        }
        byte[] eContent = dis.readNBytes(len);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv);
        return cipher.doFinal(eContent);
    }

    public static boolean isEncrypted(FileInputStream fis) throws IOException {
        long originalPosition = fis.getChannel().position();
        try {
            if (fis.getChannel().size() < HEADER_MAGIC.length) {
                return false;
            }
            fis.getChannel().position(0);
            byte[] magic = fis.readNBytes(HEADER_MAGIC.length);
            return Arrays.equals(magic, HEADER_MAGIC);
        } finally {
            fis.getChannel().position(originalPosition);
        }
    }

    public static void writeEncrypted(byte[] src, File target) throws IOException {
        if (src == null || src.length == 0) {
            throw new IOException("Source content is empty");
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            byte[] key = keyGenerator.generateKey().getEncoded();
            SecretKeySpec aesKey = new SecretKeySpec(key, "AES");
            byte[] iv = Arrays.copyOfRange(sha256.digest(key), 0, 16);
            IvParameterSpec aesIv = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv);
            byte[] eContent = cipher.doFinal(src);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(target))) {
                DataOutputStream dos = new DataOutputStream(bos);
                dos.write(HEADER_MAGIC);
                dos.writeInt(1);
                dos.writeInt(0);
                dos.write(key);
                dos.writeInt(eContent.length);
                dos.write(eContent);
                dos.flush();
            }
        } catch (Exception ex) {
            throw new IOException("Failed to encrypt file: " + target.getPath(), ex);
        }
    }

    public static void encryptIfRaw(File target) throws IOException {
        if (!target.exists() || !target.isFile()) {
            throw new IOException("Target file does not exist or is not a file: " + target.getPath());
        }
        try (FileInputStream fis = new FileInputStream(target)) {
            if (isEncrypted(fis)) {
                return;
            }
            byte[] src = IOUtils.toByteArray(fis);
            writeEncrypted(src, target);
        }
    }
}