package com.loanmanagement.service;

import com.loanmanagement.testsupport.InMemoryUserRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.loanmanagement.dao.UserRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.security.PasswordHasher;

class AuthenticationServiceTest {
    @Test
    void locksRegularUserAfterThreeFailuresAndResetsAfterSuccess() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        AuthenticationService service = new AuthenticationService(users, new PasswordHasher());
        User registered = service.register(command("Asha!Pass1"));

        AuthenticationResult first = service.authenticate("asha", "Wrong!Pass1".toCharArray());
        AuthenticationResult second = service.authenticate("asha@example.com", "Wrong!Pass1".toCharArray());
        AuthenticationResult third = service.authenticate("asha", "Wrong!Pass1".toCharArray());

        assertEquals(2, first.attemptsRemaining());
        assertEquals(1, second.attemptsRemaining());
        assertEquals(AuthenticationStatus.ACCOUNT_LOCKED, third.status());
        assertEquals(AuthenticationStatus.ACCOUNT_INACTIVE,
                service.authenticate("asha", "Asha!Pass1".toCharArray()).status());

        users.activate(registered.id());
        users.setFailedAttempts(registered.id(), 2);
        assertEquals(AuthenticationStatus.SUCCESS,
                service.authenticate("asha", "Asha!Pass1".toCharArray()).status());
        assertEquals(0, users.findByLoginIdentifier("asha").orElseThrow().failedAttempts());
    }

    @Test
    void registrationRejectsInvalidFieldsAndDuplicateIdentifiers() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        AuthenticationService service = new AuthenticationService(users, new PasswordHasher());
        service.register(command("Asha!Pass1"));

        assertThrows(IllegalArgumentException.class, () -> service.register(command("weak")));
        assertThrows(IllegalArgumentException.class, () -> service.register(
                new RegisterCommand("Asha", "Patel", "9876543210", "other@example.com", null,
                        "asha", "Another!Pass1".toCharArray())));
    }

    @Test
    void passwordResetUsesOneTimeEmailTokenAndDoesNotRevealUnknownAccount() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        AuthenticationService authentication = new AuthenticationService(users, hasher);
        authentication.register(command("Asha!Pass1"));
        String[] emailBody = { null };
        PasswordResetService resets = new PasswordResetService(
                users, hasher, (recipient, subject, body) -> emailBody[0] = body);

        resets.requestReset("asha@example.com");
        assertTrue(emailBody[0].contains("one-time password reset token"));
        String token = emailBody[0].substring(emailBody[0].lastIndexOf(' ') + 1);
        char[] newPassword = "New!StrongPass2".toCharArray();

        assertTrue(resets.resetPassword(token, newPassword));
        assertFalse(resets.resetPassword(token, newPassword));
        assertTrue(hasher.verify(newPassword, users.findByLoginIdentifier("asha").orElseThrow().passwordHash()));

        emailBody[0] = null;
        resets.requestReset("unknown-user");
        assertEquals(null, emailBody[0]);
    }

    private RegisterCommand command(String password) {
        return new RegisterCommand("Asha", "Patel", "9876543210", "asha@example.com", "Pune",
                "asha", password.toCharArray());
    }
}
