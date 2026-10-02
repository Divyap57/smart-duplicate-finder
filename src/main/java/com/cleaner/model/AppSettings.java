package com.cleaner.model;

import java.util.ArrayList;
import java.util.List;

/** User-editable settings. Stored as key/value rows in the settings table. */
public class AppSettings {
    public String defaultDir;
    public List<String> excludedDirs = new ArrayList<>();
    /** Bytes. 0 = no minimum. */
    public long minSize;
    /** Bytes. 0 = no maximum. */
    public long maxSize;
    /** SHA-256 or SHA-512 */
    public String hashAlgorithm = "SHA-256";
    public String quarantineDir;
    public boolean scanHidden;
    public boolean scanSystem;
    public boolean ignoreSystemDirs = true;
}
