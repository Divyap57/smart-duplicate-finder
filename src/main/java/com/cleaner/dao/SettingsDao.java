package com.cleaner.dao;

import com.cleaner.util.Db;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class SettingsDao {
    private SettingsDao() { }

    public static Map<String, String> all() throws SQLException {
        Map<String, String> m = new HashMap<>();
        Db.query("SELECT skey, svalue FROM settings", rs -> m.put(rs.getString(1), rs.getString(2)));
        return m;
    }

    public static void put(String key, String value) throws SQLException {
        Db.update("INSERT INTO settings (skey, svalue) VALUES (?, ?) ON DUPLICATE KEY UPDATE svalue = VALUES(svalue)", key, value);
    }
}
