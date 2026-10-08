package com.loanmanagement.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.loanmanagement.db.ConnectionFactory;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.service.FailedLoginUpdate;
import com.loanmanagement.service.RegisterCommand;

public final class JdbcUserRepository implements UserRepository, AdminUserRepository,
    AdminBootstrapRepository, CibilRepository {
    private static final String USER_COLUMNS = "id, first_name, last_name, mobile, email, location, "
            + "username, password_hash, must_change_password, role, status, failed_attempts, pan, cibil_score, "
            + "created_at, last_modified_at";

    private final ConnectionFactory connections;

    public JdbcUserRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public Optional<User> findByLoginIdentifier(String normalizedIdentifier) {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE username = ? OR email = ? LIMIT 1";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedIdentifier);
            statement.setString(2, normalizedIdentifier);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not look up account", exception);
        }
    }

    @Override
    public boolean existsByUsername(String normalizedUsername) {
        return exists("username", normalizedUsername);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return exists("email", normalizedEmail);
    }

    @Override
    public User create(RegisterCommand command, String passwordHash) {
        String sql = "INSERT INTO users (first_name, last_name, mobile, email, location, username, "
                + "password_hash, must_change_password, role, status, failed_attempts, created_at, last_modified_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, 'ROLE_USER', 'ACTIVE', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, command.firstName());
            statement.setString(2, command.lastName());
            statement.setString(3, command.mobile());
            statement.setString(4, command.email());
            statement.setString(5, command.location());
            statement.setString(6, command.username());
            statement.setString(7, passwordHash);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return generated user ID");
                }
                return findById(connection, keys.getLong(1));
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not create account; username and email must be unique", exception);
        }
    }

    @Override
    public User createGenerated(RegisterCommand command, String passwordHash, String pan, int cibilScore) {
        String sql = "INSERT INTO users (first_name, last_name, mobile, email, location, username, password_hash, "
                + "must_change_password, role, status, failed_attempts, pan, cibil_score, created_at, last_modified_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, 'ROLE_USER', 'ACTIVE', 0, ?, ?, CURRENT_TIMESTAMP(6), "
                + "CURRENT_TIMESTAMP(6))";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, command.firstName());
            statement.setString(2, command.lastName());
            statement.setString(3, command.mobile());
            statement.setString(4, command.email());
            statement.setString(5, command.location());
            statement.setString(6, command.username());
            statement.setString(7, passwordHash);
            statement.setString(8, pan);
            statement.setInt(9, cibilScore);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return generated user ID");
                }
                return findById(connection, keys.getLong(1));
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not create CIBIL onboarding account", exception);
        }
    }

    @Override
    public void changePassword(long userId, String passwordHash) {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET password_hash = ?, must_change_password = FALSE, "
                        + "last_modified_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'ACTIVE'")) {
            statement.setString(1, passwordHash);
            statement.setLong(2, userId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active account was not found");
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not change account password", exception);
        }
    }

    @Override
    public Optional<String> createPasswordResetToken(String identifier, String tokenHash, Instant expiresAt) {
        String findSql = "SELECT id, email FROM users WHERE (username = ? OR email = ?) "
                + "AND status = 'ACTIVE' AND deleted_at IS NULL AND role = 'ROLE_USER' LIMIT 1";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement find = connection.prepareStatement(findSql)) {
                find.setString(1, identifier);
                find.setString(2, identifier);
                try (ResultSet result = find.executeQuery()) {
                    if (!result.next()) {
                        connection.commit();
                        return Optional.empty();
                    }
                    long userId = result.getLong("id");
                    String email = result.getString("email");
                    try (PreparedStatement deleteOld = connection.prepareStatement(
                            "DELETE FROM password_reset_tokens WHERE user_id = ?")) {
                        deleteOld.setLong(1, userId);
                        deleteOld.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)")) {
                        insert.setLong(1, userId);
                        insert.setString(2, tokenHash);
                        insert.setTimestamp(3, Timestamp.from(expiresAt));
                        insert.executeUpdate();
                    }
                    connection.commit();
                    return Optional.of(email);
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not create password reset request", exception);
        }
    }

    @Override
    public boolean consumePasswordReset(String tokenHash, String passwordHash, Instant now) {
        String select = "SELECT user_id FROM password_reset_tokens WHERE token_hash = ? "
                + "AND expires_at > ? AND used_at IS NULL FOR UPDATE";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement query = connection.prepareStatement(select)) {
                query.setString(1, tokenHash);
                query.setTimestamp(2, Timestamp.from(now));
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) {
                        connection.commit();
                        return false;
                    }
                    long userId = result.getLong("user_id");
                    try (PreparedStatement updateUser = connection.prepareStatement(
                            "UPDATE users SET password_hash = ?, must_change_password = FALSE, "
                                    + "failed_attempts = 0, last_modified_at = CURRENT_TIMESTAMP(6) "
                                    + "WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL")) {
                        updateUser.setString(1, passwordHash);
                        updateUser.setLong(2, userId);
                        if (updateUser.executeUpdate() == 0) {
                            connection.rollback();
                            return false;
                        }
                    }
                    try (PreparedStatement consume = connection.prepareStatement(
                            "UPDATE password_reset_tokens SET used_at = ? WHERE token_hash = ? AND used_at IS NULL")) {
                        consume.setTimestamp(1, Timestamp.from(now));
                        consume.setString(2, tokenHash);
                        if (consume.executeUpdate() != 1) {
                            connection.rollback();
                            return false;
                        }
                    }
                    connection.commit();
                    return true;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not reset account password", exception);
        }
    }

    @Override
    public void updateCibil(long userId, String pan, int cibilScore) {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET pan = ?, cibil_score = ?, last_modified_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL")) {
            statement.setString(1, pan);
            statement.setInt(2, cibilScore);
            statement.setLong(3, userId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active account was not found");
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not save CIBIL assessment", exception);
        }
    }

    @Override
    public void createAdmin(String username, String email, String passwordHash) {
        String sql = "INSERT INTO users (first_name, last_name, mobile, email, location, username, "
            + "password_hash, must_change_password, role, status, failed_attempts, created_at, last_modified_at) "
            + "VALUES ('System', 'Administrator', '0000000000', ?, 'System', ?, ?, FALSE, 'ROLE_ADMIN', 'ACTIVE', "
                + "0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, username);
            statement.setString(3, passwordHash);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Could not bootstrap admin account", exception);
        }
    }

    @Override
    public FailedLoginUpdate recordFailedLogin(long userId, int lockoutThreshold) {
        String select = "SELECT failed_attempts, status FROM users WHERE id = ? AND role = 'ROLE_USER' FOR UPDATE";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement query = connection.prepareStatement(select)) {
                query.setLong(1, userId);
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("User account was not found");
                    }
                    int failures = result.getInt("failed_attempts");
                    AccountStatus status = AccountStatus.valueOf(result.getString("status"));
                    if (status == AccountStatus.ACTIVE) {
                        failures++;
                        status = failures >= lockoutThreshold ? AccountStatus.INACTIVE : AccountStatus.ACTIVE;
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE users SET failed_attempts = ?, status = ?, "
                                        + "last_modified_at = CURRENT_TIMESTAMP(6) WHERE id = ?")) {
                            update.setInt(1, failures);
                            update.setString(2, status.name());
                            update.setLong(3, userId);
                            update.executeUpdate();
                        }
                    }
                    connection.commit();
                    return new FailedLoginUpdate(status, failures);
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not update failed login count", exception);
        }
    }

    @Override
    public void resetFailedLogins(long userId) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET failed_attempts = 0, last_modified_at = CURRENT_TIMESTAMP(6) "
                                + "WHERE id = ? AND role = 'ROLE_USER'")) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Could not reset failed login count", exception);
        }
    }

    @Override
    public java.util.List<User> searchUsers(
            com.loanmanagement.service.UserSearchField field,
            String query) {
        String column = field.column();
        String searchValue = query;
        if (!field.exactMatch()) {
            column = "LOWER(" + column + ")";
            searchValue = query.toLowerCase(Locale.ROOT);
        }
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE deleted_at IS NULL AND "
                + column + " " + (field.exactMatch() ? "= ?" : "LIKE ?") + " ORDER BY id LIMIT 100";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, field.exactMatch() ? query : "%" + query + "%");
            try (ResultSet result = statement.executeQuery()) {
                java.util.List<User> matches = new java.util.ArrayList<>();
                while (result.next()) {
                    matches.add(mapUser(result));
                }
                return matches;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not search users", exception);
        }
    }

    @Override
    public Optional<User> findActiveUser(long userId) {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE id = ? AND deleted_at IS NULL";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not load user", exception);
        }
    }

    @Override
    public void updateUser(long actorId, long userId,
            com.loanmanagement.service.UserProfileUpdate update) {
        String sql = "UPDATE users SET first_name = ?, last_name = ?, mobile = ?, email = ?, location = ?, "
                + "last_modified_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND deleted_at IS NULL";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, update.firstName());
                statement.setString(2, update.lastName());
                statement.setString(3, update.mobile());
                statement.setString(4, update.email());
                statement.setString(5, update.location());
                statement.setLong(6, userId);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("User was not found or was deleted");
                }
                insertAudit(connection, actorId, "EDIT_USER", userId, "Profile updated");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not update user", exception);
        }
    }

    @Override
    public void updateStatus(long actorId, long userId, AccountStatus status) {
        String sql = "UPDATE users SET status = ?, last_modified_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND deleted_at IS NULL";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                statement.setLong(2, userId);
                statement.executeUpdate();
                insertAudit(connection, actorId,
                        status == AccountStatus.ACTIVE ? "ACTIVATE_USER" : "DEACTIVATE_USER",
                        userId, "Status set to " + status.name());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not update user status", exception);
        }
    }

    @Override
    public void anonymizeUser(long actorId, long userId) {
        String sql = "UPDATE users SET first_name = 'Deleted', last_name = 'User', mobile = '0000000000', "
                + "email = ?, location = NULL, username = ?, pan = NULL, cibil_score = NULL, status = 'INACTIVE', "
                + "deleted_at = CURRENT_TIMESTAMP(6), last_modified_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND deleted_at IS NULL";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, "deleted_" + userId + "@invalid.local");
                statement.setString(2, "deleted_" + userId);
                statement.setLong(3, userId);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("User was not found or was already deleted");
                }
                insertAudit(connection, actorId, "DELETE_USER", userId, "Personal data anonymized");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not delete user", exception);
        }
    }

    private boolean exists(String column, String value) {
        String sql = "SELECT 1 FROM users WHERE " + column + " = ? LIMIT 1";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not check account uniqueness", exception);
        }
    }

    private User findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + USER_COLUMNS + " FROM users WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Created account could not be reloaded");
                }
                return mapUser(result);
            }
        }
    }

    private void insertAudit(Connection connection, long actorId, String action, long targetId, String details)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO admin_audit_log (actor_user_id, action, target_user_id, details) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, actorId);
            statement.setString(2, action);
            statement.setLong(3, targetId);
            statement.setString(4, details);
            statement.executeUpdate();
        }
    }

    private User mapUser(ResultSet result) throws SQLException {
        Timestamp created = result.getTimestamp("created_at");
        Timestamp modified = result.getTimestamp("last_modified_at");
        int score = result.getInt("cibil_score");
        Integer cibilScore = result.wasNull() ? null : score;
        return new User(
                result.getLong("id"),
                result.getString("first_name"),
                result.getString("last_name"),
                result.getString("mobile"),
                result.getString("email"),
                result.getString("location"),
                result.getString("username"),
                result.getString("password_hash"),
                result.getBoolean("must_change_password"),
                UserRole.valueOf(result.getString("role")),
                AccountStatus.valueOf(result.getString("status")),
                result.getInt("failed_attempts"),
                result.getString("pan"),
                cibilScore,
                created == null ? Instant.EPOCH : created.toInstant(),
                modified == null ? Instant.EPOCH : modified.toInstant());
    }
}