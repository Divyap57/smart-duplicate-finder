package com.cleaner.dao;

import com.cleaner.model.FileRecord;
import com.cleaner.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FileDao {
    private static final String COLS = "id, path, dir, name, extension, size, hash, mime, created_ms, modified_ms, accessed_ms, status";

    private FileDao() { }

    static FileRecord map(ResultSet rs) throws SQLException {
        FileRecord f = new FileRecord();
        f.id = rs.getLong("id");
        f.path = rs.getString("path");
        f.dir = rs.getString("dir");
        f.name = rs.getString("name");
        f.extension = rs.getString("extension");
        f.size = rs.getLong("size");
        f.hash = rs.getString("hash");
        f.mime = rs.getString("mime");
        f.createdMs = rs.getLong("created_ms");
        f.modifiedMs = rs.getLong("modified_ms");
        f.accessedMs = rs.getLong("accessed_ms");
        f.status = rs.getString("status");
        return f;
    }

    public static FileRecord byId(long id) throws SQLException {
        return Db.one("SELECT " + COLS + " FROM files WHERE id = ?", FileDao::map, id);
    }

    public static List<FileRecord> byIds(Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        return Db.query("SELECT " + COLS + " FROM files WHERE id IN (" + Db.placeholders(ids.size()) + ")",
                FileDao::map, ids.toArray());
    }

    /** Files that currently sit at their scanned location and share this hash. */
    public static List<FileRecord> activeByHash(String hash) throws SQLException {
        return Db.query("SELECT " + COLS + " FROM files WHERE hash = ? AND status = 'ACTIVE'", FileDao::map, hash);
    }

    public static List<FileRecord> allByHash(String hash) throws SQLException {
        return Db.query("SELECT " + COLS + " FROM files WHERE hash = ? ORDER BY path", FileDao::map, hash);
    }

    public static void setStatus(long id, String status) throws SQLException {
        Db.update("UPDATE files SET status = ? WHERE id = ?", status, id);
    }

    /** Used by restore, which may have had to choose a different file name. */
    public static void setStatusAndPath(FileRecord f, String status) throws SQLException {
        Db.update("UPDATE files SET status = ?, path = ?, path_hash = ?, dir = ?, name = ? WHERE id = ?",
                status, f.path, f.pathHash(), f.dir, f.name, f.id);
    }

    /** Live duplicate rows of a scan: every ACTIVE file that belongs to a group of that scan. */
    public static List<FileRecord> duplicatesOfScan(long scanId) throws SQLException {
        return Db.query("SELECT f.id, f.path, f.dir, f.name, f.extension, f.size, f.hash, f.mime, f.created_ms, "
                + "f.modified_ms, f.accessed_ms, f.status, sf.group_id "
                + "FROM scan_files sf JOIN files f ON f.id = sf.file_id "
                + "WHERE sf.scan_id = ? AND sf.group_id IS NOT NULL AND f.status = 'ACTIVE'",
                rs -> {
                    FileRecord f = map(rs);
                    f.groupId = rs.getLong("group_id");
                    return f;
                }, scanId);
    }

    public static Long latestGroupOf(long fileId) throws SQLException {
        return Db.one("SELECT group_id FROM scan_files WHERE file_id = ? AND group_id IS NOT NULL "
                + "ORDER BY scan_id DESC LIMIT 1", rs -> rs.getLong(1), fileId);
    }

    public static List<FileRecord> largest(long scanId, int limit) throws SQLException {
        return Db.query("SELECT f.id, f.path, f.dir, f.name, f.extension, f.size, f.hash, f.mime, f.created_ms, "
                + "f.modified_ms, f.accessed_ms, f.status FROM scan_files sf JOIN files f ON f.id = sf.file_id "
                + "WHERE sf.scan_id = ? AND f.status = 'ACTIVE' ORDER BY f.size DESC LIMIT ?", FileDao::map, scanId, limit);
    }

    // ---- bulk persistence used at the end of a scan (runs inside the caller's transaction) ----

    public static void upsertAll(Connection c, List<FileRecord> files, long scanId) throws SQLException {
        String sql = "INSERT INTO files (path, path_hash, dir, name, extension, size, hash, mime, created_ms, "
                + "modified_ms, accessed_ms, status, last_scan_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?) "
                + "ON DUPLICATE KEY UPDATE dir=VALUES(dir), name=VALUES(name), extension=VALUES(extension), "
                + "size=VALUES(size), hash=VALUES(hash), mime=VALUES(mime), created_ms=VALUES(created_ms), "
                + "modified_ms=VALUES(modified_ms), accessed_ms=VALUES(accessed_ms), status='ACTIVE', "
                + "last_scan_id=VALUES(last_scan_id)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (FileRecord f : files) {
                ps.setString(1, f.path);
                ps.setString(2, f.pathHash());
                ps.setString(3, f.dir);
                ps.setString(4, f.name);
                ps.setString(5, f.extension);
                ps.setLong(6, f.size);
                if (f.hash == null) ps.setNull(7, Types.VARCHAR); else ps.setString(7, f.hash);
                ps.setString(8, f.mime);
                ps.setLong(9, f.createdMs);
                ps.setLong(10, f.modifiedMs);
                ps.setLong(11, f.accessedMs);
                ps.setLong(12, scanId);
                ps.addBatch();
                if (++n % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    /** path_hash -> file id for every file touched by this scan. */
    public static Map<String, Long> idsByPathHash(Connection c, long scanId) throws SQLException {
        Map<String, Long> m = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, path_hash FROM files WHERE last_scan_id = ?")) {
            ps.setLong(1, scanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) m.put(rs.getString(2), rs.getLong(1));
            }
        }
        return m;
    }
}
