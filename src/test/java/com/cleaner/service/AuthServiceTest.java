package com.cleaner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cleaner.dao.TestDb;
import com.cleaner.dao.UserDao;
import com.cleaner.model.User;
import com.cleaner.util.ApiException;
import com.cleaner.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @BeforeEach
    void connect() throws Exception {
        TestDb.require();
        AuthService.seedDefaultAdmin();
    }

    @Test
    void defaultAdminIsSeededOnceAndStoredHashed() throws Exception {
        AuthService.seedDefaultAdmin(); // second call must not add another
        assertEquals(1, UserDao.count());
        User u = UserDao.findByUsername("admin");
        assertNotNull(u);
        assertTrue(u.passwordHash.startsWith("pbkdf2$"));
        assertTrue(!u.passwordHash.contains("admin123"));
        assertTrue(PasswordUtil.verify("admin123", u.passwordHash));
    }

    @Test
    void correctCredentialsLogIn() throws Exception {
        assertEquals("admin", AuthService.login("admin", "admin123", "10.0.0.1").username);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameMessage() {
        ApiException a = assertThrows(ApiException.class, () -> AuthService.login("admin", "nope", "10.0.0.2"));
        ApiException b = assertThrows(ApiException.class, () -> AuthService.login("ghost", "nope", "10.0.0.2"));
        assertEquals(401, a.status);
        assertEquals(a.getMessage(), b.getMessage());
    }

    @Test
    void repeatedFailuresAreThrottled() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> AuthService.login("admin", "bad", "10.0.0.3"));
        }
        ApiException locked = assertThrows(ApiException.class, () -> AuthService.login("admin", "admin123", "10.0.0.3"));
        assertEquals(429, locked.status);
        // a different client is not affected
        assertEquals("admin", AuthService.login("admin", "admin123", "10.0.0.4").username);
    }

    @Test
    void registeredUserCanLogInAndDuplicatesAreRejected() throws Exception {
        AuthService.register("carol", "carol@example.com", "s3cret-pass", "s3cret-pass");
        User u = AuthService.login("carol", "s3cret-pass", "10.0.0.9");
        assertEquals("USER", u.role);
        assertTrue(UserDao.emailTaken("carol@example.com"));
        assertEquals(409, assertThrows(ApiException.class,
                () -> AuthService.register("carol", "other@example.com", "s3cret-pass", "s3cret-pass")).status);
        assertEquals(409, assertThrows(ApiException.class,
                () -> AuthService.register("carol2", "carol@example.com", "s3cret-pass", "s3cret-pass")).status);
    }
}
