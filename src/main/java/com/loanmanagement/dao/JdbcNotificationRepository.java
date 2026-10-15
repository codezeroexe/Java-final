package com.loanmanagement.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.loanmanagement.db.ConnectionFactory;

public final class JdbcNotificationRepository implements NotificationRepository {
    private static final int ERROR_LIMIT = 500;

    private final ConnectionFactory connections;

    public JdbcNotificationRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public void enqueue(String recipient, String subject, String body, String error) {
        try (var connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO pending_notifications (recipient, subject, body, attempts, last_error) "
                                + "VALUES (?, ?, ?, 1, ?)")) {
            statement.setString(1, recipient);
            statement.setString(2, subject);
            statement.setString(3, body);
            statement.setString(4, truncate(error));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Could not queue notification", exception);
        }
    }

    @Override
    public List<Pending> listPending(int limit) {
        try (var connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, recipient, subject, body FROM pending_notifications "
                                + "WHERE sent_at IS NULL ORDER BY id LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<Pending> pending = new ArrayList<>();
                while (rows.next()) {
                    pending.add(new Pending(rows.getLong("id"), rows.getString("recipient"),
                            rows.getString("subject"), rows.getString("body")));
                }
                return pending;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not read pending notifications", exception);
        }
    }

    @Override
    public void markSent(long id) {
        try (var connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE pending_notifications SET sent_at = CURRENT_TIMESTAMP(6), attempts = attempts + 1, "
                                + "last_error = NULL WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Could not mark notification sent", exception);
        }
    }

    @Override
    public void recordFailure(long id, String error) {
        try (var connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE pending_notifications SET attempts = attempts + 1, last_error = ? WHERE id = ?")) {
            statement.setString(1, truncate(error));
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Could not record notification failure", exception);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= ERROR_LIMIT ? value : value.substring(0, ERROR_LIMIT);
    }
}
