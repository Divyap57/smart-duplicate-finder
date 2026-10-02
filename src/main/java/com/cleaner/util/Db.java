package com.cleaner.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Tiny JDBC helper. Every statement is a PreparedStatement with bound parameters.
 * ponytail: one connection per call (no pool) - fine for a single-user local app;
 * add HikariCP if this ever serves many concurrent users.
 */
public final class Db {
    private static String url;
    private static String user;
    private static String password;

    private Db() { }

    @FunctionalInterface
    public interface Row<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static synchronized void configure(String url, String user, String password) {
        Db.url = url;
        Db.user = user;
        Db.password = password;
    }

    /** Loads config (if not set yet) and applies schema.sql. Idempotent. */
    public static synchronized void init() throws SQLException, IOException {
        if (url == null) loadConfig();
        try (Connection c = get(); Statement st = c.createStatement()) {
            for (String sql : schemaStatements()) {
                // The JDBC URL already selects the database (createDatabaseIfNotExist=true). Skipping these two
                // keeps test runs, which use a different database name, away from the real one.
                String head = sql.toUpperCase(java.util.Locale.ROOT);
                if (head.startsWith("CREATE DATABASE") || head.startsWith("USE ")) continue;
                st.execute(sql);
            }
            // Databases created before sign-up existed lack users.email. 1060 = column exists, 1061 = key exists.
            for (String alter : new String[] {"ALTER TABLE users ADD COLUMN email VARCHAR(190) NULL",
                    "ALTER TABLE users ADD UNIQUE KEY uq_users_email (email)"}) {
                try {
                    st.execute(alter);
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1060 && e.getErrorCode() != 1061) throw e;
                }
            }
        }
    }

    public static Connection get() throws SQLException {
        if (url == null) {
            try {
                loadConfig();
            } catch (IOException e) {
                throw new SQLException("Database configuration could not be loaded", e);
            }
        }
        return DriverManager.getConnection(url, user, password);
    }

    public static <T> List<T> query(String sql, Row<T> mapper, Object... args) throws SQLException {
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        }
    }

    public static <T> T one(String sql, Row<T> mapper, Object... args) throws SQLException {
        List<T> rows = query(sql, mapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static long count(String sql, Object... args) throws SQLException {
        Long n = one(sql, rs -> rs.getLong(1), args);
        return n == null ? 0 : n;
    }

    public static int update(String sql, Object... args) throws SQLException {
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    /** Executes an INSERT and returns the generated key. */
    public static long insert(String sql, Object... args) throws SQLException {
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                return k.next() ? k.getLong(1) : 0;
            }
        }
    }

    /** "?,?,?" for building IN lists. Only placeholders are generated - values are still bound. */
    public static String placeholders(int n) {
        return "?" + ",?".repeat(Math.max(0, n - 1));
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }

    private static void loadConfig() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Db.class.getResourceAsStream("/db.properties")) {
            if (in != null) p.load(in);
        }
        url = pick("DB_URL", "db.url", p);
        user = pick("DB_USER", "db.user", p);
        password = pick("DB_PASSWORD", "db.password", p);
    }

    private static String pick(String env, String prop, Properties file) {
        String v = System.getenv(env);
        if (v == null) v = System.getProperty(prop);
        if (v == null) v = file.getProperty(prop, "");
        return v;
    }

    private static List<String> schemaStatements() throws IOException {
        try (InputStream in = Db.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IOException("schema.sql not found on the classpath");
            StringBuilder sb = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.trim().startsWith("--")) sb.append(line).append('\n');
            }
            List<String> out = new ArrayList<>();
            for (String s : sb.toString().split(";\\s*\\n")) {
                String t = s.trim();
                if (t.endsWith(";")) t = t.substring(0, t.length() - 1);
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }
    }
}
