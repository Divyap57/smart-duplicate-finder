package com.cleaner.controller;

import com.cleaner.service.ScanService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/scan/progress")
public class ScanProgressServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        ok(resp, ScanService.get().progress());
    }
}
