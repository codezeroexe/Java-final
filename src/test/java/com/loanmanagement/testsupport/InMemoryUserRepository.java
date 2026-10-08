package com.loanmanagement.testsupport;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import java.util.List;

import com.loanmanagement.dao.AdminBootstrapRepository;
import com.loanmanagement.dao.AdminUserRepository;
import com.loanmanagement.dao.CibilRepository;
import com.loanmanagement.dao.UserRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.service.FailedLoginUpdate;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.service.UserProfileUpdate;
import com.loanmanagement.service.UserSearchField;

/** In-memory user store for service and screen tests. No database. */
public final class InMemoryUserRepository implements UserRepository, CibilRepository,
        AdminUserRepository, AdminBootstrapRepository {
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

    @Override
    public void updateCibil(long userId, String pan, int cibilScore) {
        User user = byId.get(userId);
        byId.put(userId, new User(user.id(), user.firstName(), user.lastName(), user.mobile(), user.email(),
                user.location(), user.username(), user.passwordHash(), user.mustChangePassword(), user.role(),
                user.status(), user.failedAttempts(), pan, cibilScore, user.createdAt(), Instant.now()));
    }

    public void activate(long userId) {
        byId.put(userId, withState(byId.get(userId), AccountStatus.ACTIVE,
                byId.get(userId).failedAttempts()));
    }

    public void setFailedAttempts(long userId, int failures) {
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

    @Override
    public List<User> searchUsers(UserSearchField field, String query) {
        return byId.values().stream()
                .filter(user -> matches(fieldValue(user, field), query, field.exactMatch()))
                .toList();
    }

    @Override
    public Optional<User> findActiveUser(long userId) {
        return Optional.ofNullable(byId.get(userId));
    }

    @Override
    public void updateUser(long actorId, long userId, UserProfileUpdate update) {
        User user = byId.get(userId);
        byId.put(userId, new User(user.id(), update.firstName(), update.lastName(), update.mobile(),
                update.email().toLowerCase(Locale.ROOT), update.location(), user.username(), user.passwordHash(),
                user.mustChangePassword(), user.role(), user.status(), user.failedAttempts(), user.pan(),
                user.cibilScore(), user.createdAt(), Instant.now()));
    }

    @Override
    public void updateStatus(long actorId, long userId, AccountStatus status) {
        User user = byId.get(userId);
        byId.put(userId, withState(user, status, user.failedAttempts()));
    }

    @Override
    public void anonymizeUser(long actorId, long userId) {
        User user = byId.get(userId);
        byId.put(userId, new User(user.id(), "Deleted", "User", "0000000000", "deleted-" + userId + "@example.invalid",
                null, "deleted-" + userId, user.passwordHash(), false, user.role(), AccountStatus.INACTIVE, 0,
                null, user.cibilScore(), user.createdAt(), Instant.now()));
    }

    @Override
    public void createAdmin(String username, String email, String passwordHash) {
        Instant now = Instant.now();
        User admin = new User(nextId++, "System", "Admin", "0000000000", email, null, username, passwordHash,
                false, UserRole.ROLE_ADMIN, AccountStatus.ACTIVE, 0, null, null, now, now);
        byId.put(admin.id(), admin);
    }

    private static String fieldValue(User user, UserSearchField field) {
        return switch (field) {
            case FIRST_NAME -> user.firstName();
            case LAST_NAME -> user.lastName();
            case EMAIL -> user.email();
            case MOBILE -> user.mobile();
            case USERNAME -> user.username();
            case STATUS -> user.status().name();
        };
    }

    private static boolean matches(String value, String query, boolean exact) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        String haystack = value.toLowerCase(Locale.ROOT);
        return exact ? haystack.equals(needle) : haystack.contains(needle);
    }
}
