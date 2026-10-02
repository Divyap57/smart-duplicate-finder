package com.cleaner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cleaner.util.ApiException;
import org.junit.jupiter.api.Test;

/** Validation runs before any database access, so these need no MySQL. */
class RegisterValidationTest {
    private static void bad(String u, String e, String p, String c) {
        assertEquals(400, assertThrows(ApiException.class, () -> AuthService.register(u, e, p, c)).status);
    }

    @Test
    void rejectsBadInput() {
        bad("ab", "a@b.com", "password1", "password1");          // username too short
        bad("bad name", "a@b.com", "password1", "password1");    // space in username
        bad("alice", "not-an-email", "password1", "password1");
        bad("alice", "a@b", "password1", "password1");           // no domain dot
        bad("alice", "a@b.com", "short", "short");               // password too short
        bad("alice", "a@b.com", "password1", "password2");       // mismatch
        bad(null, "a@b.com", "password1", "password1");
    }
}
