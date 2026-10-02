package com.cleaner.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cleaner.model.FileRecord;
import com.cleaner.model.QuarantineItem;
import com.cleaner.model.Scan;
import com.cleaner.model.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs against a real MySQL (skipped when none is reachable, see TestDb). */
class DaoTest {
    @BeforeEach
    void connect() {
        TestDb.require();
    }

    private static FileRecord file(Path dir, String name, String content, String hash) throws Exception {
        Path p = Files.writeString(dir.resolve(name), content);
        FileRecord f = new FileRecord();
        f.path = p.toString();
        f.dir = dir.toString();
        f.name = name;
        f.extension = "txt";
        f.size = content.length();
        f.hash = hash;
        f.mime = "text/plain";
        f.createdMs = 1;
        f.modifiedMs = 2;
        f.accessedMs = 3;
        return f;
    }

    @Test
    void usersAreStoredAndLookedUpByName() throws Exception {
        long id = UserDao.create("alice", "hash-value", "ADMIN");
        User u = UserDao.findByUsername("alice");
        assertEquals(id, u.id);
        assertEquals("hash-value", u.passwordHash);
        assertNull(UserDao.findByUsername("nobody"));
        assertEquals(1, UserDao.count());
        assertThrows(SQLException.class, () -> UserDao.create("alice", "x", "ADMIN")); // unique username
    }

    @Test
    void sqlInjectionAttemptsAreTreatedAsPlainData() throws Exception {
        UserDao.create("bob", "h", "ADMIN");
        assertNull(UserDao.findByUsername("bob' OR '1'='1"));
        assertNull(UserDao.findByUsername("x'; DROP TABLE users; --"));
        assertEquals(1, UserDao.count());
    }

    @Test
    void settingsUpsert() throws Exception {
        SettingsDao.put("minSize", "10");
        SettingsDao.put("minSize", "20");
        Map<String, String> all = SettingsDao.all();
        assertEquals("20", all.get("minSize"));
        assertEquals(1, all.size());
    }

    @Test
    void activityLogsAreNewestFirstAndFilterable() throws Exception {
        ActivityDao.insert("admin", "LOGIN", "Signed in", "127.0.0.1");
        ActivityDao.insert("admin", "SCAN_STARTED", "Scan #1", "127.0.0.1");
        assertEquals(2, ActivityDao.list("", 10).size());
        assertEquals("SCAN_STARTED", ActivityDao.list("", 10).get(0).action);
        assertEquals(1, ActivityDao.list("LOGIN", 10).size());
        assertEquals("Signed in", ActivityDao.list("LOGIN", 10).get(0).description);
    }

    @Test
    void scanLifecycleAndResultStorage(@TempDir Path tmp) throws Exception {
        long scanId = ScanDao.create(tmp.toString(), "admin");
        assertEquals("RUNNING", ScanDao.byId(scanId).status);

        String hash = "a".repeat(64);
        FileRecord a = file(tmp, "a.txt", "same", hash);
        FileRecord b = file(tmp, "b.txt", "same", hash);
        FileRecord c = file(tmp, "c.txt", "different!", null);
        ScanDao.saveResults(scanId, List.of(a, b, c), List.of(List.of(a, b)));

        List<FileRecord> dups = FileDao.duplicatesOfScan(scanId);
        assertEquals(2, dups.size());
        assertNotNull(dups.get(0).groupId);
        assertEquals(dups.get(0).groupId, dups.get(1).groupId);
        assertEquals(1, ScanDao.groupsOfScan(scanId, 10).size());
        assertEquals(4L, ((Number) ScanDao.groupsOfScan(scanId, 10).get(0).get("size")).longValue());
        assertEquals(4L, ((Number) ScanDao.groupsOfScan(scanId, 10).get(0).get("recoverableBytes")).longValue());
        assertEquals(3, StatsDao.byExtension(scanId).stream().mapToLong(m -> (Long) m.get("count")).sum());

        Scan row = ScanDao.byId(scanId);
        row.status = "COMPLETED";
        row.totalFiles = 3;
        row.duplicateFiles = 2;
        row.duplicateGroups = 1;
        row.recoverableBytes = 4;
        ScanDao.finish(row);
        Scan done = ScanDao.latestCompleted();
        assertEquals(scanId, done.id);
        assertEquals(4, done.recoverableBytes);
        assertNotNull(done.endedAt);

        // rescanning the same paths updates rows instead of duplicating them
        long second = ScanDao.create(tmp.toString(), "admin");
        ScanDao.saveResults(second, List.of(a, b, c), List.of(List.of(a, b)));
        assertEquals(3, com.cleaner.util.Db.count("SELECT COUNT(*) FROM files"));
        assertEquals(2, FileDao.duplicatesOfScan(second).size());
    }

    @Test
    void staleRunningScansAreMarkedFailedAfterARestart(@TempDir Path tmp) throws Exception {
        long id = ScanDao.create(tmp.toString(), "admin");
        assertEquals(1, ScanDao.failStale());
        assertEquals("FAILED", ScanDao.byId(id).status);
    }

    @Test
    void quarantineRecordsRoundTrip(@TempDir Path tmp) throws Exception {
        long scanId = ScanDao.create(tmp.toString(), "admin");
        FileRecord a = file(tmp, "a.txt", "same", "b".repeat(64));
        FileRecord b = file(tmp, "b.txt", "same", "b".repeat(64));
        ScanDao.saveResults(scanId, List.of(a, b), List.of(List.of(a, b)));
        FileRecord stored = FileDao.activeByHash("b".repeat(64)).get(0);

        QuarantineItem q = new QuarantineItem();
        q.fileId = stored.id;
        q.originalPath = stored.path;
        q.quarantinePath = tmp.resolve("q/x_a.txt").toString();
        q.name = stored.name;
        q.size = stored.size;
        q.hash = stored.hash;
        q.quarantinedAt = System.currentTimeMillis();
        q.username = "admin";
        long id = QuarantineDao.insert(q);

        assertEquals("QUARANTINED", QuarantineDao.byId(id).status);
        assertEquals(1, QuarantineDao.count("QUARANTINED"));
        assertEquals(id, QuarantineDao.activeForFile(stored.id).id);

        FileDao.setStatus(stored.id, "QUARANTINED");
        assertEquals(1, FileDao.activeByHash("b".repeat(64)).size());

        QuarantineDao.resolve(id, "RESTORED");
        assertEquals(0, QuarantineDao.count("QUARANTINED"));
        assertNotNull(QuarantineDao.byId(id).resolvedAt);
        assertFalse(QuarantineDao.list("RESTORED", 10).isEmpty());
        assertTrue(QuarantineDao.list("DELETED", 10).isEmpty());
    }
}
