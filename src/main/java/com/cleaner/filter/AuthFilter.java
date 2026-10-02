package com.cleaner.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;

/** Blocks everything except the login/sign-up pages, static assets and the login/session endpoints until signed in. */
@WebFilter(urlPatterns = "/*")
public class AuthFilter implements Filter {
    private static final Set<String> PUBLIC = Set.of("/login.html", "/signup.html", "/api/login", "/api/register", "/api/session");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "no-referrer");

        String path = req.getRequestURI().substring(req.getContextPath().length());
        int semi = path.indexOf(';');
        if (semi >= 0) path = path.substring(0, semi);

        boolean isPublic = PUBLIC.contains(path) || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/");
        HttpSession session = req.getSession(false);
        boolean signedIn = session != null && session.getAttribute("user") != null;

        if (isPublic || signedIn) {
            chain.doFilter(request, response);
        } else if (path.startsWith("/api/")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"error\":\"Please sign in.\"}");
        } else {
            resp.sendRedirect(req.getContextPath() + "/login.html");
        }
    }
}
