package com.cleaner.dao;

import com.cleaner.model.FileRecord;
import com.cleaner.model.Scan;
import com.cleaner.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScanDao {
    private static final String COLS = "id, folder, status, started_at, ended_at, total_files, total_folders, total_bytes, "
            + "duplicate_files, duplicate_groups, recoverable_bytes, skipped_files, username, message";

    private ScanDao() { }

    private static Scan map(ResultSet rs) throws SQLException {
        Scan s = new Scan();
        s.id = rs.getLong("id");
        s.folder = rs.getString("folder");
        s.status = rs.getString("status");
        s.startedAt = rs.getLong("started_at");
        long end = rs.getLong("ended_at");
        s.endedAt = rs.wasNull() ? null : end;
        s.totalFiles = rs.getLong("total_files");
        s.totalFolders = rs.getLong("total_folders");
        s.totalBytes = rs.getLong("total_bytes");
        s.duplicateFiles = rs.getLong("duplicate_files");
        s.duplicateGroups = rs.getLong("duplicate_groups");
        s.recoverableBytes = rs.getLong("recoverable_bytes");
        s.skippedFiles = rs.getLong("skipped_files");
        s.username = rs.getString("username");
        s.message = rs.getString("message");
        return s;
    }

    public static long create(String folder, String user) throws SQLException {
        return Db.insert("INSERT INTO scans (folder, status, started_at, username) VALUES (?, 'RUNNING', ?, ?)",
                folder, System.currentTimeMillis(), user);
    }

    public static void finish(Scan s) throws SQLException {
        Db.update("UPDATE scans SET status=?, ended_at=?, total_files=?, total_folders=?, total_bytes=?, "
                + "duplicate_files=?, duplicate_groups=?, recoverable_bytes=?, skipped_files=?, message=? WHERE id=?",
                s.status, System.currentTimeMillis(), s.totalFiles, s.totalFolders, s.totalBytes, s.duplicateFiles,
                s.duplicateGroups, s.recoverableBytes, s.skippedFiles, s.message, s.id);
    }

    /** A server restart while a scan was running leaves RUNNING rows behind. */
    public static int failStale() throws SQLException {
        return Db.update("UPDATE scans SET status='FAILED', ended_at=?, message='Interrupted by a server restart.' "
                + "WHERE status='RUNNING'", System.currentTimeMillis());
    }

    public static Scan byId(long id) throws SQLException {
        return Db.one("SELECT " + COLS + " FROM scans WHERE id = ?", ScanDao::map, id);
    }

    public static Scan latestCompleted() throws SQLException {
        return Db.one("SELECT " + COLS + " FROM scans WHERE status='COMPLETED' ORDER BY id DESC LIMIT 1", ScanDao::map);
    }

    public static List<Scan> recent(int limit) throws SQLException {
        return Db.query("SELECT " + COLS + " FROM scans ORDER BY id DESC LIMIT ?", ScanDao::map, limit);
    }

    /** Last {@code limit} completed scans, oldest first (for charts). */
    public static List<Scan> completedAscending(int limit) throws SQLException {
        List<Scan> l = Db.query("SELECT " + COLS + " FROM scans WHERE status='COMPLETED' ORDER BY id DESC LIMIT ?",
                ScanDao::map, limit);
        java.util.Collections.reverse(l);
        return l;
    }

    public static List<Map<String, Object>> groupsOfScan(long scanId, int limit) throws SQLException {
        return Db.query("SELECT hash, size, file_count, recoverable_bytes FROM duplicate_groups WHERE scan_id = ? "
                + "ORDER BY recoverable_bytes DESC LIMIT ?", rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("hash", rs.getString(1));
                    m.put("size", rs.getLong(2));
                    m.put("fileCount", rs.getInt(3));
                    m.put("recoverableBytes", rs.getLong(4));
                    return m;
                }, scanId, limit);
    }

    /**
     * Stores everything a finished scan found, in one transaction: file rows, duplicate groups and the
     * scan_files links. {@code groups} members must already carry their full-file hash.
     */
    public static void saveResults(long scanId, List<FileRecord> files, List<List<FileRecord>> groups) throws SQLException {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                FileDao.upsertAll(c, files, scanId);
                Map<String, Long> fileIds = FileDao.idsByPathHash(c, scanId);
                Map<String, Long> groupIds = new HashMap<>();
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO duplicate_groups "
                        + "(scan_id, hash, size, file_count, recoverable_bytes) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    for (List<FileRecord> g : groups) {
                        FileRecord first = g.get(0);
                        ps.setLong(1, scanId);
                        ps.setString(2, first.hash);
                        ps.setLong(3, first.size);
                        ps.setInt(4, g.size());
                        ps.setLong(5, first.size * (g.size() - 1));
                        ps.executeUpdate();
                        try (ResultSet k = ps.getGeneratedKeys()) {
                            if (k.next()) groupIds.put(first.hash, k.getLong(1));
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO scan_files (scan_id, file_id, group_id) VALUES (?,?,?)")) {
                    int n = 0;
                    for (FileRecord f : files) {
                        Long fid = fileIds.get(f.pathHash());
                        if (fid == null) continue;
                        Long gid = f.hash == null ? null : groupIds.get(f.hash);
                        ps.setLong(1, scanId);
                        ps.setLong(2, fid);
                        if (gid == null) ps.setNull(3, Types.BIGINT); else ps.setLong(3, gid);
                        ps.addBatch();
                        if (++n % 1000 == 0) ps.executeBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
