package com.cleaner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cleaner.dao.ActivityDao;
import com.cleaner.dao.FileDao;
import com.cleaner.dao.QuarantineDao;
import com.cleaner.dao.ScanDao;
import com.cleaner.dao.TestDb;
import com.cleaner.model.AppSettings;
import com.cleaner.model.DuplicateGroup;
import com.cleaner.model.FileRecord;
import com.cleaner.model.QuarantineItem;
import com.cleaner.model.Scan;
import com.cleaner.util.ApiException;
import com.cleaner.util.ScanControl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end: real files on disk + real MySQL. Skipped when MySQL is not reachable. */
class QuarantineServiceTest {
    private Path root;
    private Path quarantineDir;
    private long scanId;
    private Path original;
    private Path copy;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        TestDb.require();
        root = Files.createDirectories(tmp.resolve("data"));
        quarantineDir = tmp.resolve("quarantine");
        AppSettings s = SettingsService.get();
        s.quarantineDir = quarantineDir.toString();
        SettingsService.save(s);

        Files.createDirectories(root.resolve("backup"));
        original = Files.writeString(root.resolve("report.txt"), "identical content");
        copy = Files.writeString(root.resolve("backup/report-copy.txt"), "identical content");
        Files.writeString(root.resolve("other.txt"), "something unique here");

        FileScanner.Result r = FileScanner.scan(root, FileScanner.Options.defaults(), ScanControl.NONE, null);
        List<List<FileRecord>> groups = new DuplicateDetector("SHA-256", ScanControl.NONE, new DuplicateDetector.Progress() {
            public void begin(int c) { }
            public void current(FileRecord f) { }
            public void advance() { }
            public void skipped(FileRecord f, java.io.IOException e) { }
            public void group(List<FileRecord> g) { }
        }).find(r.files);
        scanId = ScanDao.create(root.toString(), "tester");
        ScanDao.saveResults(scanId, r.files, groups);
        Scan row = ScanDao.byId(scanId);
        row.status = "COMPLETED";
        ScanDao.finish(row);
    }

    private long idOf(Path p) throws Exception {
        for (DuplicateGroup g : DuplicateService.liveGroups(scanId)) {
            for (FileRecord f : g.files) if (Path.of(f.path).equals(p.toRealPath())) return f.id;
        }
        throw new AssertionError("not in a live group: " + p);
    }

    @Test
    void quarantineMovesTheFileAndRecordsItThenRestoreBringsItBack() throws Exception {
        assertEquals(1, DuplicateService.liveGroups(scanId).size());
        long copyId = idOf(copy);

        Map<String, Object> r = QuarantineService.quarantine(List.of(copyId), "tester", "127.0.0.1");
        assertEquals(1, r.get("moved"));

        assertFalse(Files.exists(copy), "original location must be empty");
        assertTrue(Files.exists(original), "the other copy stays");
        QuarantineItem item = QuarantineDao.list("QUARANTINED", 10).get(0);
        assertEquals(quarantineDir.toRealPath().resolve(Path.of(item.quarantinePath).getFileName()),
                Path.of(item.quarantinePath).toRealPath());
        assertTrue(Files.exists(Path.of(item.quarantinePath)));
        assertEquals("tester", item.username);
        assertEquals("QUARANTINED", FileDao.byId(copyId).status);
        assertTrue(DuplicateService.liveGroups(scanId).isEmpty(), "a group with one copy left is no longer a duplicate");
        assertEquals(1, ActivityDao.list("FILE_QUARANTINED", 10).size());

        Map<String, Object> back = QuarantineService.restore(List.of(item.id), "tester", "127.0.0.1");
        assertEquals(1, back.get("done"));
        assertTrue(Files.exists(copy));
        assertEquals("identical content", Files.readString(copy));
        assertFalse(Files.exists(Path.of(item.quarantinePath)));
        assertEquals("RESTORED", QuarantineDao.byId(item.id).status);
        assertEquals("ACTIVE", FileDao.byId(copyId).status);
        assertEquals(1, DuplicateService.liveGroups(scanId).size());
        assertEquals(1, ActivityDao.list("FILE_RESTORED", 10).size());
    }

    @Test
    void theLastCopyCanNeverBeQuarantined() throws Exception {
        long a = idOf(original);
        long b = idOf(copy);

        // both at once: refused for both (nothing would remain)
        Map<String, Object> r = QuarantineService.quarantine(List.of(a, b), "tester", "ip");
        assertEquals(0, r.get("moved"));
        assertTrue(Files.exists(original) && Files.exists(copy));

        // one at a time: first succeeds, the second is refused
        assertEquals(1, QuarantineService.quarantine(List.of(b), "tester", "ip").get("moved"));
        Map<String, Object> last = QuarantineService.quarantine(List.of(a), "tester", "ip");
        assertEquals(0, last.get("moved"));
        assertTrue(Files.exists(original));
    }

    @Test
    void aFileModifiedAfterTheScanIsNotMoved() throws Exception {
        long copyId = idOf(copy);
        Files.writeString(copy, "edited after the scan!!");
        Map<String, Object> r = QuarantineService.quarantine(List.of(copyId), "tester", "ip");
        assertEquals(0, r.get("moved"));
        assertTrue(Files.exists(copy));
        assertEquals(0, QuarantineDao.count("QUARANTINED"));
    }

    @Test
    void aFileThatVanishedIsReportedNotCrashed() throws Exception {
        long copyId = idOf(copy);
        Files.delete(copy);
        Map<String, Object> r = QuarantineService.quarantine(List.of(copyId), "tester", "ip");
        assertEquals(0, r.get("moved"));
        assertEquals("MISSING", FileDao.byId(copyId).status);
    }

    @Test
    void unknownIdsAndEmptySelectionsAreRejected() throws Exception {
        assertThrows(ApiException.class, () -> QuarantineService.quarantine(List.of(), "t", "ip"));
        assertThrows(ApiException.class, () -> QuarantineService.quarantine(null, "t", "ip"));
        Map<String, Object> r = QuarantineService.quarantine(List.of(999_999L), "t", "ip");
        assertEquals(0, r.get("moved"));
    }

    @Test
    void permanentDeleteNeedsConfirmationAndOnlyTouchesQuarantinedFiles() throws Exception {
        long copyId = idOf(copy);
        QuarantineService.quarantine(List.of(copyId), "tester", "ip");
        QuarantineItem item = QuarantineDao.list("QUARANTINED", 10).get(0);
        Path held = Path.of(item.quarantinePath);

        assertThrows(ApiException.class, () -> QuarantineService.deletePermanently(List.of(item.id), false, "t", "ip"));
        assertTrue(Files.exists(held));

        Map<String, Object> r = QuarantineService.deletePermanently(List.of(item.id), true, "tester", "ip");
        assertEquals(1, r.get("done"));
        assertFalse(Files.exists(held));
        assertFalse(Files.exists(copy));
        assertEquals("DELETED", QuarantineDao.byId(item.id).status);
        assertEquals(1, ActivityDao.list("FILE_DELETED", 10).size());

        // already deleted: refused, nothing else is touched
        Map<String, Object> again = QuarantineService.deletePermanently(List.of(item.id), true, "tester", "ip");
        assertEquals(0, again.get("done"));
        assertTrue(Files.exists(original));
    }

    @Test
    void restoreNeverOverwritesAndRecreatesDeletedFolders() throws Exception {
        long copyId = idOf(copy);
        QuarantineService.quarantine(List.of(copyId), "tester", "ip");
        QuarantineItem item = QuarantineDao.list("QUARANTINED", 10).get(0);

        Files.delete(root.resolve("backup")); // folder is gone
        QuarantineService.restore(List.of(item.id), "tester", "ip");
        assertEquals("identical content", Files.readString(copy));

        // quarantine again, put a different file at the original path, restore -> must pick a new name
        QuarantineService.quarantine(List.of(copyId), "tester", "ip");
        QuarantineItem second = QuarantineDao.list("QUARANTINED", 10).get(0);
        Files.writeString(copy, "someone else's file");
        QuarantineService.restore(List.of(second.id), "tester", "ip");
        assertEquals("someone else's file", Files.readString(copy));
        assertTrue(Files.exists(copy.resolveSibling("report-copy (restored).txt")));
    }

    @Test
    void quarantineLocationCannotChangeWhileFilesAreInside() throws Exception {
        QuarantineService.quarantine(List.of(idOf(copy)), "tester", "ip");
        AppSettings s = SettingsService.get();
        s.quarantineDir = quarantineDir.resolveSibling("elsewhere").toString();
        assertThrows(ApiException.class, () -> SettingsService.save(s));
    }
}
