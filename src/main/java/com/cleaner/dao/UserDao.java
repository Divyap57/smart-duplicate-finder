package com.cleaner.dao;

import com.cleaner.model.User;
import com.cleaner.util.Db;
import java.sql.SQLException;

public final class UserDao {
    private UserDao() { }

    private static User map(java.sql.ResultSet rs) throws SQLException {
        User u = new User();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.passwordHash = rs.getString("password_hash");
        u.role = rs.getString("role");
        return u;
    }

    public static User findByUsername(String username) throws SQLException {
        return Db.one("SELECT id, username, password_hash, role FROM users WHERE username = ?", UserDao::map, username);
    }

    public static long count() throws SQLException {
        return Db.count("SELECT COUNT(*) FROM users");
    }

    public static boolean emailTaken(String email) throws SQLException {
        return Db.count("SELECT COUNT(*) FROM users WHERE email = ?", email) > 0;
    }

    public static long create(String username, String email, String passwordHash, String role) throws SQLException {
        return Db.insert("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, ?)",
                username, email, passwordHash, role);
    }

    public static long create(String username, String passwordHash, String role) throws SQLException {
        return Db.insert("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)", username, passwordHash, role);
    }
}
