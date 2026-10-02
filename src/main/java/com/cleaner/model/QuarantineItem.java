package com.cleaner.model;

public class QuarantineItem {
    public long id;
    public Long fileId;
    public String originalPath;
    public String quarantinePath;
    public String name;
    public long size;
    public String hash;
    public long quarantinedAt;
    public Long resolvedAt;
    public String username;
    /** QUARANTINED, RESTORED or DELETED */
    public String status;
}
