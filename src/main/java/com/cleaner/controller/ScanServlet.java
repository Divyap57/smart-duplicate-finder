package com.cleaner.controller;

import com.cleaner.service.ScanService;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Start / pause / resume / cancel. Progress is read from /api/scan/progress. */
@WebServlet("/api/scan")
public class ScanServlet extends BaseServlet {
    public static class Command {
        public String action;
        public String folder;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Command c = body(req, Command.class);
        ScanService svc = ScanService.get();
        String action = c.action == null ? "" : c.action;
        switch (action) {
            case "start" -> svc.start(c.folder, username(req), ip(req));
            case "pause" -> svc.pause();
            case "resume" -> svc.resume();
            case "cancel" -> svc.cancel();
            default -> throw new ApiException(400, "Unknown scan action.");
        }
        ok(resp, svc.progress());
    }
}
