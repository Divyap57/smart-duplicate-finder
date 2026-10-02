package com.cleaner.service;

import com.cleaner.dao.QuarantineDao;
import com.cleaner.dao.SettingsDao;
import com.cleaner.model.AppSettings;
import com.cleaner.util.ApiException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettingsService {
    private static final Set<String> ALGORITHMS = Set.of("SHA-256", "SHA-512");

    private SettingsService() { }

    public static Path defaultQuarantineDir() {
        return Paths.get(System.getProperty("user.home"), ".smart-duplicate-finder", "quarantine");
    }

    public static AppSettings get() throws SQLException {
        Map<String, String> m = SettingsDao.all();
        AppSettings s = new AppSettings();
        s.defaultDir = m.getOrDefault("defaultDir", System.getProperty("user.home"));
        s.excludedDirs = new ArrayList<>();
        for (String line : m.getOrDefault("excludedDirs", "").split("\\R")) {
            if (!line.isBlank()) s.excludedDirs.add(line.trim());
        }
        s.minSize = parseLong(m.get("minSize"));
        s.maxSize = parseLong(m.get("maxSize"));
        s.hashAlgorithm = m.getOrDefault("hashAlgorithm", "SHA-256");
        s.quarantineDir = m.getOrDefault("quarantineDir", defaultQuarantineDir().toString());
        s.scanHidden = Boolean.parseBoolean(m.getOrDefault("scanHidden", "false"));
        s.scanSystem = Boolean.parseBoolean(m.getOrDefault("scanSystem", "false"));
        s.ignoreSystemDirs = Boolean.parseBoolean(m.getOrDefault("ignoreSystemDirs", "true"));
        return s;
    }

    public static Path quarantineDir() throws SQLException {
        return Paths.get(get().quarantineDir).toAbsolutePath().normalize();
    }

    /** Validates and stores. Throws ApiException(400) with a user-friendly message on bad input. */
    public static AppSettings save(AppSettings in) throws SQLException {
        AppSettings old = get();
        if (in.hashAlgorithm == null || !ALGORITHMS.contains(in.hashAlgorithm)) {
            throw new ApiException(400, "Hash algorithm must be SHA-256 or SHA-512.");
        }
        if (in.minSize < 0 || in.maxSize < 0) throw new ApiException(400, "File sizes cannot be negative.");
        if (in.maxSize > 0 && in.maxSize < in.minSize) {
            throw new ApiException(400, "Maximum file size must be larger than the minimum.");
        }
        Path q = absoluteDir(in.quarantineDir, "Quarantine location");
        if (q.getParent() == null) throw new ApiException(400, "Quarantine location cannot be a drive root.");
        String defaultDir = in.defaultDir == null ? "" : in.defaultDir.trim();
        if (!defaultDir.isEmpty()) absoluteDir(defaultDir, "Default scan directory");

        List<String> excluded = new ArrayList<>();
        if (in.excludedDirs != null) {
            for (String e : in.excludedDirs) {
                if (e == null || e.isBlank()) continue;
                excluded.add(absoluteDir(e, "Excluded directory").toString());
            }
        }
        boolean moved = !q.equals(Paths.get(old.quarantineDir).toAbsolutePath().normalize());
        if (moved && QuarantineDao.count("QUARANTINED") > 0) {
            throw new ApiException(409, "Restore or permanently delete the quarantined files before changing the quarantine location.");
        }

        SettingsDao.put("defaultDir", defaultDir);
        SettingsDao.put("excludedDirs", String.join("\n", excluded));
        SettingsDao.put("minSize", Long.toString(in.minSize));
        SettingsDao.put("maxSize", Long.toString(in.maxSize));
        SettingsDao.put("hashAlgorithm", in.hashAlgorithm);
        SettingsDao.put("quarantineDir", q.toString());
        SettingsDao.put("scanHidden", Boolean.toString(in.scanHidden));
        SettingsDao.put("scanSystem", Boolean.toString(in.scanSystem));
        SettingsDao.put("ignoreSystemDirs", Boolean.toString(in.ignoreSystemDirs));
        return get();
    }

    private static Path absoluteDir(String value, String label) {
        if (value == null || value.isBlank()) throw new ApiException(400, label + " is required.");
        try {
            Path p = Paths.get(value.trim());
            if (!p.isAbsolute()) throw new ApiException(400, label + " must be an absolute path.");
            return p.normalize();
        } catch (InvalidPathException e) {
            throw new ApiException(400, label + " is not a valid path.");
        }
    }

    private static long parseLong(String v) {
        try {
            return v == null ? 0 : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
