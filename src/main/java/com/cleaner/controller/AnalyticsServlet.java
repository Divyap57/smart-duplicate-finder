package com.cleaner.controller;

import com.cleaner.service.StatsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/analytics")
public class AnalyticsServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        ok(resp, StatsService.analytics());
    }
}
