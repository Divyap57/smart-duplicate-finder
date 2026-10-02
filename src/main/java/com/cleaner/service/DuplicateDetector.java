package com.cleaner.service;

import com.cleaner.model.FileRecord;
import com.cleaner.util.HashUtil;
import com.cleaner.util.ScanControl;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three-stage duplicate detection:
 * 1) group by size (different size = cannot be identical),
 * 2) for big files, hash only the first 64 KB to rule out most non-matches cheaply,
 * 3) full-file hash of whatever is left. Equal full hashes = duplicates.
 * Zero-byte files are ignored: they are all "identical" and deleting them recovers nothing.
 */
public final class DuplicateDetector {
    public static final int PARTIAL_BYTES = 64 * 1024;

    public interface Progress {
        /** Number of files that need hashing (shared a size with another file). */
        void begin(int candidates);

        void current(FileRecord f);

        void advance();

        void skipped(FileRecord f, IOException e);

        void group(List<FileRecord> group);
    }

    private final String algorithm;
    private final ScanControl control;
    private final Progress progress;

    public DuplicateDetector(String algorithm, ScanControl control, Progress progress) {
        this.algorithm = algorithm;
        this.control = control;
        this.progress = progress;
    }

    /** Returns groups of two or more identical files. Members get their full hash set; others keep hash == null. */
    public List<List<FileRecord>> find(List<FileRecord> files) {
        Map<Long, List<FileRecord>> bySize = new LinkedHashMap<>();
        for (FileRecord f : files) {
            if (f.size > 0) bySize.computeIfAbsent(f.size, k -> new ArrayList<>()).add(f);
        }
        int candidates = 0;
        for (List<FileRecord> l : bySize.values()) if (l.size() > 1) candidates += l.size();
        progress.begin(candidates);

        List<List<FileRecord>> groups = new ArrayList<>();
        for (List<FileRecord> sameSize : bySize.values()) {
            if (sameSize.size() < 2) continue;
            control.checkpoint();

            List<List<FileRecord>> toHashFully = new ArrayList<>();
            if (sameSize.get(0).size > 2L * PARTIAL_BYTES) {
                Map<String, List<FileRecord>> byPartial = new HashMap<>();
                for (FileRecord f : sameSize) {
                    progress.current(f);
                    try {
                        String p = HashUtil.partial(Paths.get(f.path), algorithm, PARTIAL_BYTES);
                        byPartial.computeIfAbsent(p, k -> new ArrayList<>()).add(f);
                    } catch (IOException e) {
                        progress.skipped(f, e);
                        progress.advance();
                    }
                }
                for (List<FileRecord> l : byPartial.values()) {
                    if (l.size() > 1) toHashFully.add(l);
                    else progress.advance(); // unique start => unique file, nothing more to do
                }
            } else {
                toHashFully.add(sameSize);
            }

            for (List<FileRecord> candidatesOfSize : toHashFully) {
                Map<String, List<FileRecord>> byHash = new HashMap<>();
                for (FileRecord f : candidatesOfSize) {
                    progress.current(f);
                    try {
                        String h = HashUtil.hash(Paths.get(f.path), algorithm, control);
                        byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(f);
                    } catch (IOException e) {
                        progress.skipped(f, e);
                    }
                    progress.advance();
                }
                for (Map.Entry<String, List<FileRecord>> e : byHash.entrySet()) {
                    if (e.getValue().size() < 2) continue;
                    for (FileRecord f : e.getValue()) f.hash = e.getKey();
                    groups.add(e.getValue());
                    progress.group(e.getValue());
                }
            }
        }
        return groups;
    }
}
