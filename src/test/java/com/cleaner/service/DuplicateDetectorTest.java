package com.cleaner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cleaner.model.FileRecord;
import com.cleaner.util.ScanControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DuplicateDetectorTest {
    /** Counts what the detector reports so tests can assert on progress too. */
    private static final class Counter implements DuplicateDetector.Progress {
        int candidates, advanced, groups, skipped;
        public void begin(int c) { candidates = c; }
        public void current(FileRecord f) { }
        public void advance() { advanced++; }
        public void skipped(FileRecord f, IOException e) { skipped++; }
        public void group(List<FileRecord> g) { groups++; }
    }

    private static List<FileRecord> scan(Path root) throws IOException {
        return FileScanner.scan(root, new FileScanner.Options(Set.of(), 0, 0, true, true, false),
                ScanControl.NONE, null).files;
    }

    private static FileRecord named(List<FileRecord> files, String name) {
        return files.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
    }

    @Test
    void groupsIdenticalFilesAndIgnoresSameSizeDifferentContent(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("b"));
        Files.writeString(tmp.resolve("photo.jpg"), "hello world");
        Files.writeString(tmp.resolve("b/photo-copy.jpg"), "hello world");
        Files.writeString(tmp.resolve("b/photo-old.jpg"), "hello world");
        Files.writeString(tmp.resolve("lookalike.txt"), "hello w0rld");   // same size, different bytes
        Files.writeString(tmp.resolve("unique.txt"), "something else entirely");

        List<FileRecord> files = scan(tmp);
        Counter c = new Counter();
        List<List<FileRecord>> groups = new DuplicateDetector("SHA-256", ScanControl.NONE, c).find(files);

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).size());
        assertEquals(1, c.groups);
        assertEquals(4, c.candidates);              // 3 copies + the same-size lookalike
        assertEquals(c.candidates, c.advanced);     // progress always completes
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", groups.get(0).get(0).hash);
        assertNull(named(files, "lookalike.txt").hash);   // only confirmed duplicates carry a hash
        assertNull(named(files, "unique.txt").hash);
    }

    @Test
    void differentSizesAreNeverHashed(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a"), "aaaa");
        Files.writeString(tmp.resolve("b"), "aaaaa");
        Counter c = new Counter();
        assertTrue(new DuplicateDetector("SHA-256", ScanControl.NONE, c).find(scan(tmp)).isEmpty());
        assertEquals(0, c.candidates);
    }

    @Test
    void largeFilesThatOnlyDifferAtTheEndAreNotMergedByThePartialHash(@TempDir Path tmp) throws Exception {
        byte[] data = new byte[300_000];
        new Random(42).nextBytes(data);
        byte[] changedTail = data.clone();
        changedTail[changedTail.length - 1] ^= 0x55;
        Files.write(tmp.resolve("big-1.bin"), data);
        Files.write(tmp.resolve("big-1-copy.bin"), data);
        Files.write(tmp.resolve("big-2.bin"), changedTail);

        List<List<FileRecord>> groups = new DuplicateDetector("SHA-256", ScanControl.NONE, new Counter()).find(scan(tmp));
        assertEquals(1, groups.size());
        assertEquals(Set.of("big-1.bin", "big-1-copy.bin"),
                Set.of(groups.get(0).get(0).name, groups.get(0).get(1).name));
    }

    @Test
    void largeFilesWithDifferentStartsAreDiscardedAfterThePartialHash(@TempDir Path tmp) throws Exception {
        byte[] a = new byte[200_000];
        byte[] b = new byte[200_000];
        new Random(1).nextBytes(a);
        new Random(2).nextBytes(b);
        Files.write(tmp.resolve("a.bin"), a);
        Files.write(tmp.resolve("b.bin"), b);
        Counter c = new Counter();
        assertTrue(new DuplicateDetector("SHA-256", ScanControl.NONE, c).find(scan(tmp)).isEmpty());
        assertEquals(2, c.advanced);
    }

    @Test
    void emptyFilesAreNotReportedAsDuplicates(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("e1"), new byte[0]);
        Files.write(tmp.resolve("e2"), new byte[0]);
        assertTrue(new DuplicateDetector("SHA-256", ScanControl.NONE, new Counter()).find(scan(tmp)).isEmpty());
    }

    @Test
    void sha512GivesTheSameGroupingWithLongerHashes(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("x"), "same");
        Files.writeString(tmp.resolve("y"), "same");
        List<List<FileRecord>> g = new DuplicateDetector("SHA-512", ScanControl.NONE, new Counter()).find(scan(tmp));
        assertEquals(1, g.size());
        assertEquals(128, g.get(0).get(0).hash.length());
    }

    @Test
    void anUnreadableFileIsSkippedWithoutAbortingTheRest(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("ok1"), "dup!");
        Files.writeString(tmp.resolve("ok2"), "dup!");
        Files.writeString(tmp.resolve("vanishes"), "dup!");
        List<FileRecord> files = scan(tmp);
        Files.delete(tmp.resolve("vanishes")); // deleted between discovery and hashing

        Counter c = new Counter();
        List<List<FileRecord>> groups = new DuplicateDetector("SHA-256", ScanControl.NONE, c).find(files);
        assertEquals(1, c.skipped);
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).size());
        assertNotNull(groups.get(0).get(0).hash);
    }

    @Test
    void recoverableSpaceIsSizeTimesExtraCopies(@TempDir Path tmp) throws Exception {
        for (String n : new String[] {"a", "b", "c", "d"}) Files.write(tmp.resolve(n), new byte[1000]);
        List<List<FileRecord>> groups = new DuplicateDetector("SHA-256", ScanControl.NONE, new Counter()).find(scan(tmp));
        List<FileRecord> g = groups.get(0);
        assertEquals(3000, g.get(0).size * (g.size() - 1));
        assertEquals(4, new ArrayList<>(g).size());
    }
}
