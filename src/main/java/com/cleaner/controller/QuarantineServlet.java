package com.cleaner.controller;

import com.cleaner.dao.QuarantineDao;
import com.cleaner.model.QuarantineItem;
import com.cleaner.service.QuarantineService;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GET lists quarantine entries (?status=QUARANTINED|RESTORED|DELETED|ALL); POST moves duplicate files in. */
@WebServlet("/api/quarantine")
public class QuarantineServlet extends BaseServlet {
    private static final Set<String> STATUSES = Set.of("QUARANTINED", "RESTORED", "DELETED", "ALL");

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String status = req.getParameter("status") == null ? "QUARANTINED" : req.getParameter("status");
        if (!STATUSES.contains(status)) throw new ApiException(400, "Unknown status.");
        List<QuarantineItem> items = QuarantineDao.list("ALL".equals(status) ? "" : status, 1000);
        long bytes = 0;
        for (QuarantineItem i : items) bytes += i.size;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("totalBytes", bytes);
        ok(resp, out);
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        IdsBody b = body(req, IdsBody.class);
        ok(resp, QuarantineService.quarantine(b.ids, username(req), ip(req)));
    }
}
