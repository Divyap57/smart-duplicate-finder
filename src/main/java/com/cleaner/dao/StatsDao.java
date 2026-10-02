package com.cleaner.dao;

import com.cleaner.util.Db;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregate queries for the dashboard, analytics and storage pages. */
public final class StatsDao {
    private StatsDao() { }

    /** One row per extension: extension, file count, total bytes. */
    public static List<Map<String, Object>> byExtension(long scanId) throws SQLException {
        return Db.query("SELECT f.extension AS ext, COUNT(*) AS n, SUM(f.size) AS b FROM scan_files sf "
                + "JOIN files f ON f.id = sf.file_id WHERE sf.scan_id = ? GROUP BY f.extension", rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("extension", rs.getString("ext"));
                    m.put("count", rs.getLong("n"));
                    m.put("bytes", rs.getLong("b"));
                    return m;
                }, scanId);
    }

    public static List<Map<String, Object>> topFolders(long scanId, int limit) throws SQLException {
        return Db.query("SELECT f.dir AS d, COUNT(*) AS n, SUM(f.size) AS b FROM scan_files sf "
                + "JOIN files f ON f.id = sf.file_id WHERE sf.scan_id = ? GROUP BY f.dir ORDER BY b DESC LIMIT ?", rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("folder", rs.getString("d"));
                    m.put("files", rs.getLong("n"));
                    m.put("bytes", rs.getLong("b"));
                    return m;
                }, scanId, limit);
    }
}
