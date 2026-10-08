package com.loanmanagement.service;

import com.loanmanagement.dao.UserRepository;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.validation.InputValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

public final class PasswordResetService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final EmailSender emailSender;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordHasher passwordHasher, EmailSender emailSender) {
        this(users, passwordHasher, emailSender, Clock.systemUTC());
    }

    PasswordResetService(UserRepository users, PasswordHasher passwordHasher, EmailSender emailSender, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    public void requestReset(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        byte[] randomToken = new byte[32];
        RANDOM.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        Instant now = clock.instant();
        users.createPasswordResetToken(normalize(identifier), hashToken(token), now.plus(TOKEN_LIFETIME))
                .ifPresent(email -> sendResetEmail(email, token));
    }

    public boolean resetPassword(String token, char[] newPassword) {
        if (token == null || token.isBlank()
                || !InputValidator.isValidPassword(newPassword == null ? null : new String(newPassword))) {
            throw new IllegalArgumentException("Reset token or new password is invalid");
        }
        return users.consumePasswordReset(hashToken(token.trim()), passwordHasher.hash(newPassword), clock.instant());
    }

    private void sendResetEmail(String email, String token) {
        try {
            emailSender.send(email, "Password Reset Request",
                    "Use this one-time password reset token within 15 minutes: " + token);
        } catch (EmailDeliveryException ignored) {
            // Keep response independent of account existence; expired token will not alter account.
        }
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }
}