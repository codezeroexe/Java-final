package com.loanmanagement.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void hashesAndVerifiesPasswordWithoutPersistingPlaintext() {
        PasswordHasher hasher = new PasswordHasher();
        char[] password = "Strong!Pass1".toCharArray();

        String firstHash = hasher.hash(password);
        String secondHash = hasher.hash(password);

        assertNotEquals(firstHash, secondHash);
        assertTrue(hasher.verify(password, firstHash));
        assertFalse(hasher.verify("Wrong!Pass1".toCharArray(), firstHash));
        assertFalse(hasher.verify(password, "not-a-hash"));
    }
}