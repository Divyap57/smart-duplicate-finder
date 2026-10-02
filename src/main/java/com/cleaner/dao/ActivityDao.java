package com.cleaner.dao;

import com.cleaner.model.ActivityLog;
import com.cleaner.util.Db;
import java.sql.SQLException;
import java.util.List;

public final class ActivityDao {
    private ActivityDao() { }

    private static ActivityLog map(java.sql.ResultSet rs) throws SQLException {
        ActivityLog a = new ActivityLog();
        a.id = rs.getLong("id");
        a.ts = rs.getLong("ts");
        a.username = rs.getString("username");
        a.action = rs.getString("action");
        a.description = rs.getString("description");
        a.ip = rs.getString("ip");
        return a;
    }

    public static void insert(String user, String action, String description, String ip) throws SQLException {
        String d = description.length() > 1000 ? description.substring(0, 1000) : description;
        Db.update("INSERT INTO activity_logs (ts, username, action, description, ip) VALUES (?, ?, ?, ?, ?)",
                System.currentTimeMillis(), user, action, d, ip);
    }

    /** {@code action} may be empty for "all actions". */
    public static List<ActivityLog> list(String action, int limit) throws SQLException {
        return Db.query("SELECT id, ts, username, action, description, ip FROM activity_logs "
                + "WHERE (? = '' OR action = ?) ORDER BY ts DESC, id DESC LIMIT ?",
                ActivityDao::map, action, action, limit);
    }
}
