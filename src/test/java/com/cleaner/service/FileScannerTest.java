package com.cleaner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cleaner.model.FileRecord;
import com.cleaner.util.ScanCancelledException;
import com.cleaner.util.ScanControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScannerTest {
    private static FileScanner.Options all() {
        return new FileScanner.Options(Set.of(), 0, 0, true, true, false);
    }

    private static Set<String> names(FileScanner.Result r) {
        return r.files.stream().map(f -> f.name).collect(Collectors.toSet());
    }

    @Test
    void scansRecursivelyAndCollectsMetadata(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("a/b"));
        Files.writeString(tmp.resolve("top.txt"), "12345");
        Files.writeString(tmp.resolve("a/b/Deep.JPG"), "xyz");

        FileScanner.Result r = FileScanner.scan(tmp, all(), ScanControl.NONE, null);

        assertEquals(Set.of("top.txt", "Deep.JPG"), names(r));
        assertEquals(3, r.folders); // root, a, a/b
        assertEquals(8, r.totalBytes);
        FileRecord deep = r.files.stream().filter(f -> f.name.equals("Deep.JPG")).findFirst().orElseThrow();
        assertEquals("jpg", deep.extension);
        assertEquals(3, deep.size);
        assertEquals("image/jpeg", deep.mime);
        assertTrue(deep.modifiedMs > 0);
        assertEquals(tmp.resolve("a/b").toRealPath().toString(), deep.dir);
    }

    @Test
    void sizeFiltersAndExcludedFoldersAreHonoured(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("skip"));
        Files.write(tmp.resolve("tiny"), new byte[2]);
        Files.write(tmp.resolve("mid"), new byte[50]);
        Files.write(tmp.resolve("huge"), new byte[500]);
        Files.write(tmp.resolve("skip/inside"), new byte[50]);

        Set<Path> excluded = Set.of(tmp.toRealPath().resolve("skip"));
        FileScanner.Result r = FileScanner.scan(tmp, new FileScanner.Options(excluded, 10, 100, true, true, false),
                ScanControl.NONE, null);
        assertEquals(Set.of("mid"), names(r));
    }

    @Test
    void hiddenFilesAndFoldersAreSkippedUnlessEnabled(@TempDir Path tmp) throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "dot-files are only 'hidden' on Unix");
        Files.createDirectories(tmp.resolve(".cache"));
        Files.writeString(tmp.resolve(".secret"), "x");
        Files.writeString(tmp.resolve(".cache/file"), "x");
        Files.writeString(tmp.resolve("visible"), "x");

        assertEquals(Set.of("visible"), names(FileScanner.scan(tmp,
                new FileScanner.Options(Set.of(), 0, 0, false, false, false), ScanControl.NONE, null)));
        assertEquals(Set.of("visible", ".secret", "file"), names(FileScanner.scan(tmp, all(), ScanControl.NONE, null)));
    }

    @Test
    void symbolicLinksAreNeverFollowedOrListed(@TempDir Path tmp) throws Exception {
        Path real = Files.createDirectories(tmp.resolve("real"));
        Files.writeString(real.resolve("f.txt"), "x");
        try {
            Files.createSymbolicLink(tmp.resolve("link-to-dir"), real);
            Files.createSymbolicLink(tmp.resolve("link-to-file"), real.resolve("f.txt"));
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "cannot create symlinks here");
        }
        FileScanner.Result r = FileScanner.scan(tmp, all(), ScanControl.NONE, null);
        assertEquals(1, r.files.size());
        assertEquals("f.txt", r.files.get(0).name);
    }

    @Test
    void commonSystemDirectoriesAreIgnoredWhenRequested(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("$RECYCLE.BIN"));
        Files.writeString(tmp.resolve("$RECYCLE.BIN/junk"), "x");
        Files.writeString(tmp.resolve("keep"), "x");
        FileScanner.Options on = new FileScanner.Options(Set.of(), 0, 0, true, true, true);
        assertEquals(Set.of("keep"), names(FileScanner.scan(tmp, on, ScanControl.NONE, null)));
        assertEquals(Set.of("keep", "junk"), names(FileScanner.scan(tmp, all(), ScanControl.NONE, null)));
    }

    @Test
    void unreadableOrMissingRootFailsWithFriendlyMessage(@TempDir Path tmp) {
        IOException e = assertThrows(IOException.class,
                () -> FileScanner.scan(tmp.resolve("does-not-exist"), all(), ScanControl.NONE, null));
        assertEquals("Unable to access this folder.", e.getMessage());
    }

    @Test
    void cancellingStopsTheWalk(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a"), "x");
        assertThrows(ScanCancelledException.class, () -> FileScanner.scan(tmp, all(), () -> {
            throw new ScanCancelledException();
        }, null));
    }

    @Test
    void listenerSeesEveryFileAndFolder(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("d"));
        Files.writeString(tmp.resolve("d/one"), "1");
        Files.writeString(tmp.resolve("two"), "2");
        int[] dirs = {0};
        List<String> files = new java.util.ArrayList<>();
        FileScanner.scan(tmp, all(), ScanControl.NONE, new FileScanner.Listener() {
            public void onDirectory(Path d) { dirs[0]++; }
            public void onFile(Path f) { files.add(f.getFileName().toString()); }
        });
        assertEquals(2, dirs[0]);
        assertTrue(files.containsAll(List.of("one", "two")));
        assertFalse(files.contains("d"));
    }
}
