package com.cleaner.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HashUtil {
    private static final int BUFFER = 1 << 20;

    private HashUtil() { }

    /** Hash of the whole file as lower-case hex. Checks {@code control} between reads. */
    public static String hash(Path file, String algorithm, ScanControl control) throws IOException {
        MessageDigest md = digest(algorithm);
        byte[] buf = new byte[BUFFER];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                if (control != null) control.checkpoint();
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Hash of the first {@code bytes} bytes only; used to cheaply rule out big files. */
    public static String partial(Path file, String algorithm, int bytes) throws IOException {
        MessageDigest md = digest(algorithm);
        byte[] buf = new byte[bytes];
        try (InputStream in = Files.newInputStream(file)) {
            int total = 0;
            int n;
            while (total < bytes && (n = in.read(buf, total, bytes - total)) > 0) total += n;
            md.update(buf, 0, total);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** SHA-512 hex strings are 128 chars, SHA-256 are 64. */
    public static String algorithmForHex(String hex) {
        return hex != null && hex.length() == 128 ? "SHA-512" : "SHA-256";
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithm);
        }
    }
}
