package com.cleaner.dao;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cleaner.util.Db;
import java.sql.SQLException;
import java.util.List;

/**
 * Connects the DB-backed tests to a throw-away database (smart_duplicate_finder_test) and empties it.
 * If MySQL is not reachable the calling test is SKIPPED with the reason, not failed.
 * Override with TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD (falls back to DB_USER / DB_PASSWORD, then root/root).
 */
public final class TestDb {
    private TestDb() { }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null ? fallback : v;
    }

    public static void require() {
        Db.configure(
                env("TEST_DB_URL", "jdbc:mysql://localhost:3306/smart_duplicate_finder_test"
                        + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"),
                env("TEST_DB_USER", env("DB_USER", "root")),
                env("TEST_DB_PASSWORD", env("DB_PASSWORD", "root")));
        try {
            Db.init();
            reset();
        } catch (Exception e) {
            assumeTrue(false, "MySQL test database not reachable - DB tests skipped: " + e.getMessage());
        }
    }

    /** Child tables first so foreign keys never block the deletes. */
    public static void reset() throws SQLException {
        for (String table : List.of("scan_files", "quarantine", "duplicate_groups", "files", "activity_logs", "scans",
                "settings", "users")) {
            Db.update("DELETE FROM " + table);
        }
    }
}
