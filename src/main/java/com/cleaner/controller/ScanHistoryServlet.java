package com.cleaner.controller;

import com.cleaner.dao.ScanDao;
import com.cleaner.model.Scan;
import com.cleaner.service.StatsService;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/scans (list) and GET /api/scans/{id} (details). */
@WebServlet(urlPatterns = {"/api/scans", "/api/scans/*"})
public class ScanHistoryServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (req.getPathInfo() == null) {
            ok(resp, Map.of("scans", ScanDao.recent(200)));
            return;
        }
        Scan s = ScanDao.byId(pathId(req));
        if (s == null) throw new ApiException(404, "Scan not found.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scan", s);
        m.put("topGroups", ScanDao.groupsOfScan(s.id, 20));
        m.put("types", "COMPLETED".equals(s.status) ? StatsService.types(s.id) : java.util.List.of());
        ok(resp, m);
    }
}
