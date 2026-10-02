package com.cleaner.controller;

import com.cleaner.service.ActivityService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/logout")
public class LogoutServlet extends BaseServlet {
    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String user = username(req);
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        if (user != null) ActivityService.log(user, "LOGOUT", "Signed out", ip(req));
        ok(resp, Map.of("ok", true));
    }
}
