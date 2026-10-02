package com.cleaner.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** One scanned file. Public fields so Gson can serialise it directly. */
public class FileRecord {
    public long id;
    public String path;
    public String dir;
    public String name;
    public String extension = "";
    public long size;
    /** Full-file hash. Only set for files that ended up in a duplicate group. */
    public String hash;
    public String mime;
    public long createdMs;
    public long modifiedMs;
    public long accessedMs;
    public String status = "ACTIVE";
    public Long groupId;

    private transient String pathHash;

    /** SHA-256 of the path string; the unique key of the files table. */
    public String pathHash() {
        if (pathHash == null) {
            try {
                pathHash = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return pathHash;
    }
}
