package com.loanmanagement.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaMigrator {
    private static final int CURRENT_VERSION = 1;

    private final ConnectionFactory connections;

    public SchemaMigrator(ConnectionFactory connections) {
        this.connections = connections;
    }

    public void migrate() throws SQLException {
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INT NOT NULL PRIMARY KEY,
                        applied_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    )
                    """);

            if (isApplied(connection, CURRENT_VERSION)) {
                return;
            }

            String migration = readMigration();
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql.trim());
                }
            }
            statement.executeUpdate("INSERT INTO schema_migrations (version) VALUES (" + CURRENT_VERSION + ")");
        }
    }

    private boolean isApplied(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String readMigration() {
        try (InputStream stream = SchemaMigrator.class.getResourceAsStream("/db/migration/V1__initial_schema.sql")) {
            if (stream == null) {
                throw new IllegalStateException("Initial database migration is missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read database migration", exception);
        }
    }
}