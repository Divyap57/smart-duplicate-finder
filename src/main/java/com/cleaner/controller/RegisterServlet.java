package com.cleaner.controller;

import com.cleaner.service.ActivityService;
import com.cleaner.service.AuthService;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/** POST /api/register - creates an account; the user then signs in normally. */
@WebServlet("/api/register")
public class RegisterServlet extends BaseServlet {
    public static class Form {
        public String username;
        public String email;
        public String password;
        public String confirm;
    }

    @Override
    protected boolean csrfRequired() {
        return false; // no session (and so no token) exists before signing in, same as login
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Form f = body(req, Form.class);
        if (f == null || f.username == null || f.email == null || f.password == null) {
            throw new ApiException(400, "Please fill in every field.");
        }
        String username = f.username.trim();
        AuthService.register(username, f.email.trim(), f.password, f.confirm);
        ActivityService.log(username, "USER_REGISTERED", "Account created", ip(req));
        ok(resp, Map.of("created", true));
    }
}
