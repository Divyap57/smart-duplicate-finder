package com.cleaner.controller;

import com.cleaner.service.StatsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Data for the Storage Analyzer page. */
@WebServlet("/api/storage")
public class StorageServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        ok(resp, StatsService.storage());
    }
}
