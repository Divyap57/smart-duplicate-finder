package com.cleaner.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lets each page learn who is signed in and get the CSRF token for its requests. Public on purpose. */
@WebServlet("/api/session")
public class SessionServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        HttpSession s = req.getSession(false);
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null || s.getAttribute("user") == null) {
            m.put("authenticated", false);
        } else {
            m.put("authenticated", true);
            m.put("username", s.getAttribute("user"));
            m.put("csrf", s.getAttribute("csrf"));
        }
        ok(resp, m);
    }
}
