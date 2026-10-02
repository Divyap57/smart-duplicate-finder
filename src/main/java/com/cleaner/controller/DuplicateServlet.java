package com.cleaner.controller;

import com.cleaner.dao.ScanDao;
import com.cleaner.model.DuplicateGroup;
import com.cleaner.model.Scan;
import com.cleaner.service.DuplicateService;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GET /api/duplicates?scan=&q=&type=&minSize=&sort=&offset=&limit=
 * Groups come from the latest completed scan (or ?scan=) and exclude files quarantined since.
 */
@WebServlet("/api/duplicates")
public class DuplicateServlet extends BaseServlet {
    private static final int MAX_LIMIT = 500;

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        long scanId = longParam(req, "scan", 0);
        Scan scan = scanId > 0 ? ScanDao.byId(scanId) : ScanDao.latestCompleted();
        Map<String, Object> out = new LinkedHashMap<>();
        if (scan == null) {
            out.put("hasScan", false);
            out.put("groups", List.of());
            out.put("totalGroups", 0);
            out.put("totalFiles", 0);
            out.put("recoverableBytes", 0);
            ok(resp, out);
            return;
        }
        if (!"COMPLETED".equals(scan.status)) throw new ApiException(400, "That scan has no results.");

        String q = param(req, "q").toLowerCase(Locale.ROOT);
        String type = param(req, "type");
        long minSize = longParam(req, "minSize", 0);
        String sort = param(req, "sort");
        int offset = (int) Math.max(0, longParam(req, "offset", 0));
        int limit = (int) Math.min(MAX_LIMIT, Math.max(1, longParam(req, "limit", 100)));

        List<DuplicateGroup> groups = new ArrayList<>();
        for (DuplicateGroup g : DuplicateService.liveGroups(scan.id)) {
            if (g.size < minSize) continue;
            if (!type.isEmpty() && !type.equals(g.category)) continue;
            if (!q.isEmpty() && !matches(g, q)) continue;
            groups.add(g);
        }
        groups.sort(comparator(sort));

        long files = 0;
        long recoverable = 0;
        for (DuplicateGroup g : groups) {
            files += g.fileCount;
            recoverable += g.recoverableBytes;
        }
        out.put("hasScan", true);
        out.put("scanId", scan.id);
        out.put("totalGroups", groups.size());
        out.put("totalFiles", files);
        out.put("recoverableBytes", recoverable);
        out.put("groups", groups.subList(Math.min(offset, groups.size()), Math.min(groups.size(), offset + limit)));
        ok(resp, out);
    }

    private static boolean matches(DuplicateGroup g, String q) {
        if (g.hash.startsWith(q)) return true;
        return g.files.stream().anyMatch(f -> f.name.toLowerCase(Locale.ROOT).contains(q)
                || f.path.toLowerCase(Locale.ROOT).contains(q));
    }

    private static Comparator<DuplicateGroup> comparator(String sort) {
        return switch (sort) {
            case "name" -> Comparator.comparing((DuplicateGroup g) -> g.files.get(0).name.toLowerCase(Locale.ROOT));
            case "date" -> Comparator.comparingLong((DuplicateGroup g) -> g.newestModifiedMs).reversed();
            case "files" -> Comparator.comparingInt((DuplicateGroup g) -> g.fileCount).reversed();
            case "size" -> Comparator.comparingLong((DuplicateGroup g) -> g.size).reversed();
            default -> Comparator.comparingLong((DuplicateGroup g) -> g.recoverableBytes).reversed(); // "recoverable"
        };
    }

    private static String param(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? "" : v.trim();
    }
}
