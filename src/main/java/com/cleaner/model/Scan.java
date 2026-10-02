package com.cleaner.model;

public class Scan {
    public long id;
    public String folder;
    /** RUNNING, COMPLETED, CANCELLED or FAILED */
    public String status;
    public long startedAt;
    public Long endedAt;
    public long totalFiles;
    public long totalFolders;
    public long totalBytes;
    public long duplicateFiles;
    public long duplicateGroups;
    public long recoverableBytes;
    public long skippedFiles;
    public String username;
    public String message;
}
