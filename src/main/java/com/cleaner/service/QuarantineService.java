package com.cleaner.service;

import com.cleaner.dao.FileDao;
import com.cleaner.dao.QuarantineDao;
import com.cleaner.model.FileRecord;
import com.cleaner.model.QuarantineItem;
import com.cleaner.util.ApiException;
import com.cleaner.util.FileMover;
import com.cleaner.util.HashUtil;
import com.cleaner.util.ScanControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quarantine, restore and permanent delete. The browser only ever supplies database ids; every path
 * comes from the database and is re-checked before anything touches the disk.
 */
public final class QuarantineService {
    private static final Logger LOG = Logger.getLogger(QuarantineService.class.getName());
    private static final int MAX_BATCH = 5000;

    private QuarantineService() { }

    public static Map<String, Object> quarantine(Collection<Long> fileIds, String user, String ip) throws SQLException {
        Set<Long> ids = checkIds(fileIds);
        Path qdir = SettingsService.quarantineDir();
        Map<Long, FileRecord> found = new HashMap<>();
        for (FileRecord f : FileDao.byIds(ids)) found.put(f.id, f);

        List<Map<String, Object>> results = new ArrayList<>();
        int moved = 0;
        long bytes = 0;
        for (Long id : ids) {
            FileRecord f = found.get(id);
            String problem;
            try {
                problem = f == null ? "File not found." : check(f, qdir, ids);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "File could not be read: " + (f == null ? id : f.path), e);
                problem = "File could not be read.";
            }
            if (problem == null) {
                try {
                    move(f, qdir, user);
                    moved++;
                    bytes += f.size;
                    ActivityService.log(user, "FILE_QUARANTINED", "Quarantined " + f.path, ip);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Quarantine failed for " + f.path, e);
                    problem = "File could not be moved to quarantine.";
                }
            }
            results.add(result(id, problem));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("moved", moved);
        out.put("failed", results.size() - moved);
        out.put("bytes", bytes);
        out.put("results", results);
        return out;
    }

    /** Returns null when the file may be quarantined, otherwise a user-facing reason. */
    private static String check(FileRecord f, Path qdir, Set<Long> batch) throws SQLException, IOException {
        if (!"ACTIVE".equals(f.status)) return "File is not available.";
        if (f.hash == null) return "Only confirmed duplicates can be quarantined.";
        Path p = Paths.get(f.path).toAbsolutePath().normalize();
        if (p.startsWith(qdir)) return "File is already in quarantine.";
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            FileDao.setStatus(f.id, "MISSING");
            return "File no longer exists.";
        }
        // Another copy must survive: same hash, ACTIVE, not part of this batch, still on disk.
        boolean survivor = false;
        for (FileRecord other : FileDao.activeByHash(f.hash)) {
            if (!batch.contains(other.id) && Files.isRegularFile(Paths.get(other.path), LinkOption.NOFOLLOW_LINKS)) {
                survivor = true;
                break;
            }
        }
        if (!survivor) return "At least one copy must be kept.";
        // The file may have changed since the scan; only move what is still byte-identical to the group.
        String current = HashUtil.hash(p, HashUtil.algorithmForHex(f.hash), ScanControl.NONE);
        if (!current.equals(f.hash)) return "File changed since the scan. Please scan again.";
        return null;
    }

    private static void move(FileRecord f, Path qdir, String user) throws IOException, SQLException {
        Path src = Paths.get(f.path).toAbsolutePath().normalize();
        Path dest = FileMover.moveIn(src, qdir, f.name);
        try {
            QuarantineItem q = new QuarantineItem();
            q.fileId = f.id;
            q.originalPath = src.toString();
            q.quarantinePath = dest.toString();
            q.name = f.name;
            q.size = f.size;
            q.hash = f.hash;
            q.quarantinedAt = System.currentTimeMillis();
            q.username = user;
            QuarantineDao.insert(q);
            FileDao.setStatus(f.id, "QUARANTINED");
        } catch (SQLException | RuntimeException e) {
            try {
                Files.move(dest, src); // keep disk and database consistent
            } catch (IOException undo) {
                LOG.log(Level.SEVERE, "Could not undo quarantine move of " + src, undo);
            }
            throw e;
        }
    }

    public static Map<String, Object> restore(Collection<Long> quarantineIds, String user, String ip) throws SQLException {
        Set<Long> ids = checkIds(quarantineIds);
        Path qdir = SettingsService.quarantineDir();
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        for (QuarantineItem q : QuarantineDao.byIds(ids)) {
            String problem = null;
            try {
                Path from = insideQuarantine(q, qdir);
                if (!Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS)) {
                    problem = "The quarantined file is missing from disk.";
                } else {
                    Path original = Paths.get(q.originalPath).toAbsolutePath().normalize();
                    if (original.startsWith(qdir)) throw new ApiException(400, "Invalid restore location.");
                    Path dest = FileMover.moveBack(from, original);
                    QuarantineDao.resolve(q.id, "RESTORED");
                    if (q.fileId != null) {
                        FileRecord f = FileDao.byId(q.fileId);
                        if (f != null) {
                            f.path = dest.toString();
                            f.dir = dest.getParent() == null ? "" : dest.getParent().toString();
                            f.name = dest.getFileName().toString();
                            FileDao.setStatusAndPath(f, "ACTIVE");
                        }
                    }
                    ok++;
                    ActivityService.log(user, "FILE_RESTORED", "Restored " + dest, ip);
                }
            } catch (ApiException e) {
                problem = e.getMessage();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Restore failed for quarantine item " + q.id, e);
                problem = "File could not be restored.";
            }
            results.add(result(q.id, problem));
        }
        return summary(ok, results);
    }

    public static Map<String, Object> deletePermanently(Collection<Long> quarantineIds, boolean confirmed, String user, String ip)
            throws SQLException {
        if (!confirmed) throw new ApiException(400, "Permanent deletion must be confirmed.");
        Set<Long> ids = checkIds(quarantineIds);
        Path qdir = SettingsService.quarantineDir();
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        for (QuarantineItem q : QuarantineDao.byIds(ids)) {
            String problem = null;
            try {
                Path p = insideQuarantine(q, qdir);
                Files.deleteIfExists(p);
                QuarantineDao.resolve(q.id, "DELETED");
                ok++;
                ActivityService.log(user, "FILE_DELETED", "Permanently deleted " + q.originalPath, ip);
            } catch (ApiException e) {
                problem = e.getMessage();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Delete failed for quarantine item " + q.id, e);
                problem = "File could not be deleted.";
            }
            results.add(result(q.id, problem));
        }
        return summary(ok, results);
    }

    /** The item must still be QUARANTINED and its file must live directly inside the quarantine folder. */
    private static Path insideQuarantine(QuarantineItem q, Path qdir) {
        if (!"QUARANTINED".equals(q.status)) throw new ApiException(409, "This item is no longer in quarantine.");
        Path p = Paths.get(q.quarantinePath).toAbsolutePath().normalize();
        if (!qdir.equals(p.getParent())) throw new ApiException(400, "Refusing to touch a file outside the quarantine folder.");
        return p;
    }

    private static Set<Long> checkIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new ApiException(400, "Nothing was selected.");
        if (ids.size() > MAX_BATCH) throw new ApiException(400, "Too many items selected at once.");
        return new LinkedHashSet<>(ids);
    }

    private static Map<String, Object> result(long id, String problem) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("ok", problem == null);
        if (problem != null) r.put("message", problem);
        return r;
    }

    private static Map<String, Object> summary(int ok, List<Map<String, Object>> results) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("done", ok);
        out.put("failed", results.size() - ok);
        out.put("results", results);
        return out;
    }
}
