package com.loanmanagement.dao;

import java.util.Optional;
import java.time.Instant;

import com.loanmanagement.model.User;
import com.loanmanagement.service.FailedLoginUpdate;
import com.loanmanagement.service.RegisterCommand;

public interface UserRepository {
    Optional<User> findByLoginIdentifier(String normalizedIdentifier);

    boolean existsByUsername(String normalizedUsername);

    boolean existsByEmail(String normalizedEmail);

    User create(RegisterCommand command, String passwordHash);

    User createGenerated(RegisterCommand command, String passwordHash, String pan, int cibilScore);

    void changePassword(long userId, String passwordHash);

    Optional<String> createPasswordResetToken(String normalizedIdentifier, String tokenHash, Instant expiresAt);

    boolean consumePasswordReset(String tokenHash, String passwordHash, Instant now);

    FailedLoginUpdate recordFailedLogin(long userId, int lockoutThreshold);

    void resetFailedLogins(long userId);
}