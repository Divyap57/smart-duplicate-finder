package com.cleaner.dao;

import com.cleaner.model.QuarantineItem;
import com.cleaner.util.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuarantineDao {
    private static final String COLS = "id, file_id, original_path, quarantine_path, name, size, hash, quarantined_at, "
            + "resolved_at, username, status";

    private QuarantineDao() { }

    private static QuarantineItem map(ResultSet rs) throws SQLException {
        QuarantineItem q = new QuarantineItem();
        q.id = rs.getLong("id");
        long fid = rs.getLong("file_id");
        q.fileId = rs.wasNull() ? null : fid;
        q.originalPath = rs.getString("original_path");
        q.quarantinePath = rs.getString("quarantine_path");
        q.name = rs.getString("name");
        q.size = rs.getLong("size");
        q.hash = rs.getString("hash");
        q.quarantinedAt = rs.getLong("quarantined_at");
        long res = rs.getLong("resolved_at");
        q.resolvedAt = rs.wasNull() ? null : res;
        q.username = rs.getString("username");
        q.status = rs.getString("status");
        return q;
    }

    public static long insert(QuarantineItem q) throws SQLException {
        return Db.insert("INSERT INTO quarantine (file_id, original_path, quarantine_path, name, size, hash, "
                + "quarantined_at, username, status) VALUES (?,?,?,?,?,?,?,?,'QUARANTINED')",
                q.fileId, q.originalPath, q.quarantinePath, q.name, q.size, q.hash, q.quarantinedAt, q.username);
    }

    public static QuarantineItem byId(long id) throws SQLException {
        return Db.one("SELECT " + COLS + " FROM quarantine WHERE id = ?", QuarantineDao::map, id);
    }

    public static List<QuarantineItem> byIds(Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        return Db.query("SELECT " + COLS + " FROM quarantine WHERE id IN (" + Db.placeholders(ids.size()) + ")",
                QuarantineDao::map, ids.toArray());
    }

    /** {@code status} may be empty for "all". */
    public static List<QuarantineItem> list(String status, int limit) throws SQLException {
        return Db.query("SELECT " + COLS + " FROM quarantine WHERE (? = '' OR status = ?) "
                + "ORDER BY quarantined_at DESC, id DESC LIMIT ?", QuarantineDao::map, status, status, limit);
    }

    public static QuarantineItem activeForFile(long fileId) throws SQLException {
        return Db.one("SELECT " + COLS + " FROM quarantine WHERE file_id = ? AND status = 'QUARANTINED' "
                + "ORDER BY id DESC LIMIT 1", QuarantineDao::map, fileId);
    }

    public static void resolve(long id, String status) throws SQLException {
        Db.update("UPDATE quarantine SET status = ?, resolved_at = ? WHERE id = ?", status, System.currentTimeMillis(), id);
    }

    public static long count(String status) throws SQLException {
        return Db.count("SELECT COUNT(*) FROM quarantine WHERE status = ?", status);
    }

    /** Per-day totals of space freed (quarantined or deleted), UTC days. */
    public static List<Map<String, Object>> freedByDay() throws SQLException {
        return Db.query("SELECT FLOOR(quarantined_at / 86400000) AS d, COUNT(*) AS n, SUM(size) AS b FROM quarantine "
                + "WHERE status IN ('QUARANTINED','DELETED') GROUP BY d ORDER BY d", rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("dayMs", rs.getLong("d") * 86400000L);
                    m.put("files", rs.getLong("n"));
                    m.put("bytes", rs.getLong("b"));
                    return m;
                });
    }
}
