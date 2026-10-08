package com.loanmanagement.service;

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

    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<Long, User> byId = new HashMap<>();
        private final Map<String, ResetToken> resetTokens = new HashMap<>();
        private long nextId = 1;

        @Override
        public Optional<User> findByLoginIdentifier(String identifier) {
            return byId.values().stream()
                    .filter(user -> user.username().equals(identifier) || user.email().equals(identifier))
                    .findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return byId.values().stream().anyMatch(user -> user.username().equals(username));
        }

        @Override
        public boolean existsByEmail(String email) {
            return byId.values().stream().anyMatch(user -> user.email().equals(email));
        }

        @Override
        public User create(RegisterCommand command, String passwordHash) {
            Instant now = Instant.now();
            User user = new User(nextId++, command.firstName(), command.lastName(), command.mobile(),
                    command.email().toLowerCase(Locale.ROOT), command.location(), command.username(),
                    passwordHash, false, UserRole.ROLE_USER, AccountStatus.ACTIVE, 0, null, null, now, now);
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public User createGenerated(RegisterCommand command, String passwordHash, String pan, int cibilScore) {
            User user = create(command, passwordHash);
            User generated = new User(user.id(), user.firstName(), user.lastName(), user.mobile(), user.email(),
                    user.location(), user.username(), user.passwordHash(), true, user.role(), user.status(),
                    user.failedAttempts(), pan, cibilScore, user.createdAt(), user.lastModifiedAt());
            byId.put(generated.id(), generated);
            return generated;
        }

        @Override
        public void changePassword(long userId, String passwordHash) {
            User user = byId.get(userId);
            byId.put(userId, new User(user.id(), user.firstName(), user.lastName(), user.mobile(), user.email(),
                    user.location(), user.username(), passwordHash, false, user.role(), user.status(),
                    user.failedAttempts(), user.pan(), user.cibilScore(), user.createdAt(), Instant.now()));
        }

        @Override
        public Optional<String> createPasswordResetToken(String identifier, String tokenHash, Instant expiresAt) {
            Optional<User> user = findByLoginIdentifier(identifier);
            user.ifPresent(account -> resetTokens.put(tokenHash, new ResetToken(account.id(), expiresAt)));
            return user.map(User::email);
        }

        @Override
        public boolean consumePasswordReset(String tokenHash, String passwordHash, Instant now) {
            ResetToken token = resetTokens.get(tokenHash);
            if (token == null || !token.expiresAt().isAfter(now)) {
                return false;
            }
            changePassword(token.userId(), passwordHash);
            resetTokens.remove(tokenHash);
            return true;
        }

        @Override
        public FailedLoginUpdate recordFailedLogin(long userId, int lockoutThreshold) {
            User user = byId.get(userId);
            int failures = user.failedAttempts() + 1;
            AccountStatus status = failures >= lockoutThreshold ? AccountStatus.INACTIVE : user.status();
            byId.put(userId, withState(user, status, failures));
            return new FailedLoginUpdate(status, failures);
        }

        @Override
        public void resetFailedLogins(long userId) {
            byId.put(userId, withState(byId.get(userId), AccountStatus.ACTIVE, 0));
        }

        private void activate(long userId) {
            byId.put(userId, withState(byId.get(userId), AccountStatus.ACTIVE,
                    byId.get(userId).failedAttempts()));
        }

        private void setFailedAttempts(long userId, int failures) {
            byId.put(userId, withState(byId.get(userId), AccountStatus.ACTIVE, failures));
        }

        private User withState(User user, AccountStatus status, int failures) {
            return new User(user.id(), user.firstName(), user.lastName(), user.mobile(), user.email(),
                    user.location(), user.username(), user.passwordHash(), user.mustChangePassword(),
                    user.role(), status, failures,
                    user.pan(), user.cibilScore(), user.createdAt(), Instant.now());
        }

        private record ResetToken(long userId, Instant expiresAt) {
        }
    }
}