package com.cleaner.service;

import com.cleaner.dao.FileDao;
import com.cleaner.model.DuplicateGroup;
import com.cleaner.model.FileRecord;
import com.cleaner.util.FileUtil;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DuplicateService {
    private DuplicateService() { }

    /**
     * The duplicate groups of a scan as they look right now: files that were quarantined, restored elsewhere or
     * lost since the scan are excluded, and groups left with fewer than two copies disappear.
     */
    public static List<DuplicateGroup> liveGroups(long scanId) throws SQLException {
        Map<Long, DuplicateGroup> byId = new LinkedHashMap<>();
        for (FileRecord f : FileDao.duplicatesOfScan(scanId)) {
            DuplicateGroup g = byId.computeIfAbsent(f.groupId, id -> {
                DuplicateGroup n = new DuplicateGroup();
                n.id = id;
                n.hash = f.hash;
                n.size = f.size;
                n.category = FileUtil.category(f.extension);
                return n;
            });
            g.files.add(f);
            g.newestModifiedMs = Math.max(g.newestModifiedMs, f.modifiedMs);
        }
        List<DuplicateGroup> out = new ArrayList<>();
        for (DuplicateGroup g : byId.values()) {
            if (g.files.size() < 2) continue;
            g.fileCount = g.files.size();
            g.recoverableBytes = g.size * (g.fileCount - 1);
            out.add(g);
        }
        return out;
    }
}
