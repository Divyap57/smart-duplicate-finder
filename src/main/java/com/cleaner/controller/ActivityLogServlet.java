package com.cleaner.controller;

import com.cleaner.dao.ActivityDao;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;

/** GET /api/logs?action=&limit= */
@WebServlet("/api/logs")
public class ActivityLogServlet extends BaseServlet {
    static final Set<String> ACTIONS = Set.of("LOGIN", "LOGOUT", "USER_REGISTERED", "SCAN_STARTED", "SCAN_COMPLETED", "SCAN_CANCELLED",
            "SCAN_FAILED", "FILE_QUARANTINED", "FILE_RESTORED", "FILE_DELETED", "SETTINGS_CHANGED");

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String action = req.getParameter("action") == null ? "" : req.getParameter("action");
        if (!action.isEmpty() && !ACTIONS.contains(action)) throw new ApiException(400, "Unknown action.");
        int limit = (int) Math.min(1000, Math.max(1, longParam(req, "limit", 200)));
        ok(resp, Map.of("logs", ActivityDao.list(action, limit), "actions", ACTIONS.stream().sorted().toList()));
    }
}
