package com.loanmanagement.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Applies each migration once, in version order, tracked in {@code schema_migrations}. */
public final class SchemaMigrator {
    private record Migration(int version, String resource) {
    }

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "/db/migration/V1__initial_schema.sql"),
            new Migration(2, "/db/migration/V2__pending_notifications.sql"));

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
            for (Migration migration : MIGRATIONS) {
                if (isApplied(connection, migration.version())) {
                    continue;
                }
                for (String sql : readMigration(migration.resource()).split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql.trim());
                    }
                }
                statement.executeUpdate("INSERT INTO schema_migrations (version) VALUES (" + migration.version() + ")");
            }
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

    private String readMigration(String resource) {
        try (InputStream stream = SchemaMigrator.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Database migration is missing: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read database migration", exception);
        }
    }
}
