package com.cleaner.controller;

import com.cleaner.model.AppSettings;
import com.cleaner.service.ActivityService;
import com.cleaner.service.SettingsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/settings")
public class SettingsServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        ok(resp, SettingsService.get());
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        AppSettings saved = SettingsService.save(body(req, AppSettings.class));
        ActivityService.log(username(req), "SETTINGS_CHANGED", "Settings were updated", ip(req));
        ok(resp, saved);
    }
}
