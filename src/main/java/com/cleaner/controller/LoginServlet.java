package com.cleaner.controller;

import com.cleaner.service.ActivityService;
import com.cleaner.service.AuthService;
import com.cleaner.model.User;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

@WebServlet("/api/login")
public class LoginServlet extends BaseServlet {
    private static final SecureRandom RNG = new SecureRandom();

    public static class Credentials {
        public String username;
        public String password;
    }

    @Override
    protected boolean csrfRequired() {
        return false;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Credentials c = body(req, Credentials.class);
        if (c.username == null || c.username.isBlank() || c.password == null || c.password.isEmpty()
                || c.username.length() > 64 || c.password.length() > 200) {
            throw new ApiException(400, "Please enter your username and password.");
        }
        User u;
        try {
            u = AuthService.login(c.username.trim(), c.password, ip(req));
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate(); // new session id after login: no session fixation
        HttpSession s = req.getSession(true);
        byte[] t = new byte[24];
        RNG.nextBytes(t);
        String csrf = HexFormat.of().formatHex(t);
        s.setAttribute("user", u.username);
        s.setAttribute("role", u.role);
        s.setAttribute("csrf", csrf);
        s.setMaxInactiveInterval(60 * 60);
        ActivityService.log(u.username, "LOGIN", "Signed in", ip(req));
        ok(resp, Map.of("username", u.username, "csrf", csrf));
    }
}
