package com.cleaner.service;

import com.cleaner.dao.ActivityDao;
import com.cleaner.dao.FileDao;
import com.cleaner.dao.QuarantineDao;
import com.cleaner.dao.ScanDao;
import com.cleaner.dao.StatsDao;
import com.cleaner.model.DuplicateGroup;
import com.cleaner.model.FileRecord;
import com.cleaner.model.Scan;
import com.cleaner.util.FileUtil;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the JSON payloads of the dashboard, analytics and storage pages from real database data. */
public final class StatsService {
    private StatsService() { }

    public static Map<String, Object> dashboard() throws SQLException {
        Scan last = ScanDao.latestCompleted();
        long dupFiles = 0;
        long dupGroups = 0;
        long recoverable = 0;
        if (last != null) {
            for (DuplicateGroup g : DuplicateService.liveGroups(last.id)) {
                dupGroups++;
                dupFiles += g.fileCount;
                recoverable += g.recoverableBytes;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasScan", last != null);
        m.put("totalFiles", last == null ? 0 : last.totalFiles);
        m.put("totalFolders", last == null ? 0 : last.totalFolders);
        m.put("totalBytes", last == null ? 0 : last.totalBytes);
        m.put("duplicateGroups", dupGroups);
        m.put("duplicateFiles", dupFiles);
        m.put("recoverableBytes", recoverable);
        m.put("quarantined", QuarantineDao.count("QUARANTINED"));
        m.put("lastScanAt", last == null ? null : last.endedAt);
        m.put("lastScanFolder", last == null ? null : last.folder);
        m.put("types", last == null ? List.of() : types(last.id));
        m.put("scanHistory", ScanDao.completedAscending(10));
        m.put("recentScans", ScanDao.recent(5));
        m.put("recentActivity", ActivityDao.list("", 8));
        return m;
    }

    public static Map<String, Object> analytics() throws SQLException {
        Scan last = ScanDao.latestCompleted();
        List<Map<String, Object>> top = new ArrayList<>();
        if (last != null) {
            List<DuplicateGroup> groups = DuplicateService.liveGroups(last.id);
            groups.sort(Comparator.comparingLong((DuplicateGroup g) -> g.recoverableBytes).reversed());
            for (DuplicateGroup g : groups.subList(0, Math.min(10, groups.size()))) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", g.files.get(0).name);
                t.put("files", g.fileCount);
                t.put("size", g.size);
                t.put("recoverableBytes", g.recoverableBytes);
                top.add(t);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scans", ScanDao.completedAscending(30));
        m.put("freedByDay", QuarantineDao.freedByDay());
        m.put("types", last == null ? List.of() : types(last.id));
        m.put("topGroups", top);
        return m;
    }

    public static Map<String, Object> storage() throws SQLException {
        Scan last = ScanDao.latestCompleted();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasScan", last != null);
        if (last == null) return m;
        List<Map<String, Object>> largest = new ArrayList<>();
        for (FileRecord f : FileDao.largest(last.id, 25)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", f.id);
            r.put("name", f.name);
            r.put("path", f.path);
            r.put("size", f.size);
            r.put("modifiedMs", f.modifiedMs);
            largest.add(r);
        }
        m.put("scan", last);
        m.put("types", types(last.id));
        m.put("topFolders", StatsDao.topFolders(last.id, 15));
        m.put("largestFiles", largest);
        return m;
    }

    /** Extension rows folded into categories (Images, Videos, ...), biggest first. */
    public static List<Map<String, Object>> types(long scanId) throws SQLException {
        Map<String, long[]> acc = new LinkedHashMap<>();
        for (Map<String, Object> row : StatsDao.byExtension(scanId)) {
            long[] a = acc.computeIfAbsent(FileUtil.category((String) row.get("extension")), k -> new long[2]);
            a[0] += (Long) row.get("count");
            a[1] += (Long) row.get("bytes");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        acc.forEach((cat, a) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("category", cat);
            r.put("count", a[0]);
            r.put("bytes", a[1]);
            out.add(r);
        });
        out.sort((x, y) -> Long.compare((Long) y.get("bytes"), (Long) x.get("bytes")));
        return out;
    }
}
