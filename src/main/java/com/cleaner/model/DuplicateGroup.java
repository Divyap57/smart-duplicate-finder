package com.cleaner.model;

import java.util.ArrayList;
import java.util.List;

public class DuplicateGroup {
    public long id;
    public String hash;
    public long size;
    public int fileCount;
    public long recoverableBytes;
    public String category;
    public long newestModifiedMs;
    public List<FileRecord> files = new ArrayList<>();
}
