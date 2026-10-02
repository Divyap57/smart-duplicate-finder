package com.cleaner.controller;

import com.cleaner.util.ApiException;
import com.cleaner.util.Json;
import com.google.gson.JsonParseException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared behaviour of all JSON endpoints: JSON in/out, CSRF check on state-changing requests, and one place
 * that turns exceptions into friendly messages (stack traces only go to the server log).
 */
public abstract class BaseServlet extends HttpServlet {
    protected static final Logger LOG = Logger.getLogger("com.cleaner");

    /** Login is the only endpoint that cannot require a token (none exists before signing in). */
    protected boolean csrfRequired() {
        return true;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            String m = req.getMethod();
            if (!m.equals("GET") && !m.equals("HEAD") && csrfRequired()) checkCsrf(req);
            super.service(req, resp);
        } catch (ApiException e) {
            send(resp, e.status, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Request failed: " + req.getMethod() + " " + req.getRequestURI(), e);
            send(resp, 500, Map.of("error", "Something went wrong. Please try again."));
        }
    }

    /** Subclasses implement get/post; they may throw anything, service() maps it to a safe response. */
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        throw new ApiException(405, "Method not allowed.");
    }

    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        throw new ApiException(405, "Method not allowed.");
    }

    @Override
    protected final void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        run(() -> get(req, resp));
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        run(() -> post(req, resp));
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static void run(Action a) throws ServletException, IOException {
        try {
            a.run();
        } catch (RuntimeException | ServletException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static void checkCsrf(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        String expected = s == null ? null : (String) s.getAttribute("csrf");
        String given = req.getHeader("X-CSRF-Token");
        if (expected == null || given == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(403, "Your session has expired. Please reload the page.");
        }
    }

    protected static void send(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(Json.G.toJson(body));
    }

    protected static void ok(HttpServletResponse resp, Object body) throws IOException {
        send(resp, 200, body);
    }

    protected static <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        try (Reader r = req.getReader()) {
            T v = Json.G.fromJson(r, type);
            if (v == null) throw new ApiException(400, "Request body is required.");
            return v;
        } catch (JsonParseException e) {
            throw new ApiException(400, "The request could not be understood.");
        }
    }

    /** Body of the form {"ids":[1,2,3]} used by quarantine / restore / delete. */
    public static class IdsBody {
        public List<Long> ids;
        public boolean confirm;
    }

    protected static String username(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (String) s.getAttribute("user");
    }

    protected static String ip(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    protected static long longParam(HttpServletRequest req, String name, long fallback) {
        String v = req.getParameter(name);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid value for " + name + ".");
        }
    }

    /** Last path segment as an id: /api/files/42 -> 42. */
    protected static long pathId(HttpServletRequest req) {
        String info = req.getPathInfo();
        if (info == null || info.length() < 2) throw new ApiException(400, "An id is required.");
        try {
            return Long.parseLong(info.substring(1));
        } catch (NumberFormatException e) {
            throw new ApiException(404, "Not found.");
        }
    }
}
