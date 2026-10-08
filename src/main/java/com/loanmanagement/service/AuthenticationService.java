package com.loanmanagement.service;

import java.util.Locale;
import java.util.Optional;

import com.loanmanagement.dao.UserRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.validation.InputValidator;

public final class AuthenticationService {
    private static final int LOCKOUT_THRESHOLD = 3;

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public AuthenticationService(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    public User register(RegisterCommand command) {
        return register(command, false, null, null);
    }

    public User registerGenerated(RegisterCommand command, String pan, int cibilScore) {
        if (!InputValidator.isAlphabeticName(command.firstName())
                || !InputValidator.isAlphabeticName(command.lastName())) {
            throw new IllegalArgumentException("First and last name must contain only letters");
        }
        if (!InputValidator.isValidPan(pan)) {
            throw new IllegalArgumentException("Invalid PAN format. Please enter a valid PAN.");
        }
        if (cibilScore < 600 || cibilScore > 900) {
            throw new IllegalArgumentException("CIBIL score must be between 600 and 900");
        }
        return register(command, true, pan, cibilScore);
    }

    public void changePassword(User user, char[] newPassword) {
        if (user == null || user.status() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Active account required to change password");
        }
        if (!InputValidator.isValidPassword(newPassword == null ? null : new String(newPassword))) {
            throw new IllegalArgumentException("Password does not meet the required policy");
        }
        users.changePassword(user.id(), passwordHasher.hash(newPassword));
    }

    private User register(RegisterCommand command, boolean mustChangePassword, String pan, Integer cibilScore) {
        validateRegistration(command);

        String username = normalize(command.username());
        String email = normalize(command.email());
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already in use");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already in use");
        }

        String passwordHash = passwordHasher.hash(command.password());
        RegisterCommand normalized = new RegisterCommand(
                command.firstName().trim(),
                command.lastName().trim(),
                command.mobile().trim(),
                email,
                blankToNull(command.location()),
                username,
                command.password());
        return mustChangePassword
            ? users.createGenerated(normalized, passwordHash, pan, cibilScore)
            : users.create(normalized, passwordHash);
    }

    public AuthenticationResult authenticate(String identifier, char[] password) {
        if (identifier == null || identifier.isBlank() || password == null || password.length == 0) {
            return result(AuthenticationStatus.INVALID_CREDENTIALS, Optional.empty(), 0);
        }

        Optional<User> candidate = users.findByLoginIdentifier(normalize(identifier));
        if (candidate.isEmpty()) {
            return result(AuthenticationStatus.INVALID_CREDENTIALS, Optional.empty(), 0);
        }

        User user = candidate.get();
        if (user.status() != AccountStatus.ACTIVE) {
            return result(AuthenticationStatus.ACCOUNT_INACTIVE, Optional.empty(), 0);
        }
        if (!passwordHasher.verify(password, user.passwordHash())) {
            if (user.role() == UserRole.ROLE_ADMIN) {
                return result(AuthenticationStatus.INVALID_CREDENTIALS, Optional.empty(), 0);
            }

            FailedLoginUpdate update = users.recordFailedLogin(user.id(), LOCKOUT_THRESHOLD);
            if (update.accountStatus() == AccountStatus.INACTIVE) {
                return result(AuthenticationStatus.ACCOUNT_LOCKED, Optional.empty(), 0);
            }
            return result(
                    AuthenticationStatus.INVALID_CREDENTIALS,
                    Optional.empty(),
                    Math.max(0, LOCKOUT_THRESHOLD - update.failedAttempts()));
        }

        if (user.role() == UserRole.ROLE_USER && user.failedAttempts() > 0) {
            users.resetFailedLogins(user.id());
        }
        return result(AuthenticationStatus.SUCCESS, Optional.of(user), 0);
    }

    private void validateRegistration(RegisterCommand command) {
        if (command == null
                || isBlank(command.firstName())
                || isBlank(command.lastName())
                || isBlank(command.mobile())
                || isBlank(command.email())
                || isBlank(command.username())
                || command.password() == null
                || command.password().length == 0) {
            throw new IllegalArgumentException("Required registration fields must not be empty");
        }
        if (!InputValidator.isValidMobile(command.mobile())) {
            throw new IllegalArgumentException("Mobile number must contain exactly 10 digits");
        }
        if (!InputValidator.isValidEmail(command.email())) {
            throw new IllegalArgumentException("Email address is invalid");
        }
        if (!InputValidator.isValidPassword(new String(command.password()))) {
            throw new IllegalArgumentException("Password does not meet the required policy");
        }
    }

    private AuthenticationResult result(
            AuthenticationStatus status,
            Optional<User> user,
            int attemptsRemaining) {
        return new AuthenticationResult(status, user, attemptsRemaining);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}