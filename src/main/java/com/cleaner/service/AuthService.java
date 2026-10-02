package com.cleaner.service;

import com.cleaner.dao.UserDao;
import com.cleaner.model.User;
import com.cleaner.util.ApiException;
import com.cleaner.util.PasswordUtil;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 5 * 60_000L;
    private static final Map<String, Deque<Long>> FAILURES = new ConcurrentHashMap<>();
    /** Verified against when the user does not exist, so timing does not reveal valid usernames. */
    private static final String DUMMY_HASH = PasswordUtil.hash("not-a-real-password");

    private AuthService() { }

    /** Creates the development account (admin / admin123) when the users table is empty. */
    public static void seedDefaultAdmin() throws SQLException {
        if (UserDao.count() == 0) UserDao.create("admin", PasswordUtil.hash("admin123"), "ADMIN");
    }

    private static final java.util.regex.Pattern USERNAME = java.util.regex.Pattern.compile("[A-Za-z0-9_.-]{3,32}");
    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("[^\\s@]{1,64}@[^\\s@]{1,120}\\.[^\\s@.]{2,}");

    /** Creates a normal (non-admin) account. Validation lives here so the browser checks are only a convenience. */
    public static void register(String username, String email, String password, String confirm) throws SQLException {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ApiException(400, "Username must be 3-32 characters: letters, numbers, dot, dash or underscore.");
        }
        if (email == null || email.length() > 190 || !EMAIL.matcher(email).matches()) {
            throw new ApiException(400, "Please enter a valid email address.");
        }
        if (password == null || password.length() < 8 || password.length() > 200) {
            throw new ApiException(400, "Password must be at least 8 characters.");
        }
        if (!password.equals(confirm)) throw new ApiException(400, "Passwords do not match.");
        if (UserDao.findByUsername(username) != null) throw new ApiException(409, "That username is already taken.");
        if (UserDao.emailTaken(email)) throw new ApiException(409, "An account with that email already exists.");
        try {
            UserDao.create(username, email, PasswordUtil.hash(password), "USER");
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) { // lost a race with a concurrent sign-up
                throw new ApiException(409, "That username or email is already registered.");
            }
            throw e;
        }
    }

    public static User login(String username, String password, String ip) throws SQLException {
        String key = ip + "|" + username.toLowerCase();
        Deque<Long> fails = FAILURES.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (fails) {
            while (!fails.isEmpty() && now - fails.peekFirst() > WINDOW_MS) fails.pollFirst();
            if (fails.size() >= MAX_FAILURES) {
                throw new ApiException(429, "Too many failed attempts. Please wait a few minutes and try again.");
            }
        }
        User u = UserDao.findByUsername(username);
        boolean ok = PasswordUtil.verify(password, u == null ? DUMMY_HASH : u.passwordHash) && u != null;
        if (!ok) {
            synchronized (fails) {
                fails.addLast(now);
            }
            throw new ApiException(401, "Invalid username or password.");
        }
        FAILURES.remove(key);
        return u;
    }
}
