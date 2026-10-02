package com.cleaner.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {
    @Test
    void correctPasswordVerifiesAndWrongOneDoesNot() {
        String h = PasswordUtil.hash("admin123");
        assertTrue(PasswordUtil.verify("admin123", h));
        assertFalse(PasswordUtil.verify("admin124", h));
        assertFalse(PasswordUtil.verify("", h));
    }

    @Test
    void hashesAreSaltedAndNeverContainThePassword() {
        String a = PasswordUtil.hash("same");
        String b = PasswordUtil.hash("same");
        assertNotEquals(a, b);
        assertFalse(a.contains("same"));
    }

    @Test
    void malformedStoredHashIsRejectedNotThrown() {
        assertFalse(PasswordUtil.verify("x", "garbage"));
        assertFalse(PasswordUtil.verify("x", "pbkdf2$abc$%%%$%%%"));
        assertFalse(PasswordUtil.verify(null, "pbkdf2$1$AA==$AA=="));
    }
}
