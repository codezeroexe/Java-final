package com.loanmanagement.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionFactory {
    private final String url;
    private final String username;
    private final String password;

    public ConnectionFactory(String url, String username, String password) {
        this.url = requireValue(url, "DB_URL");
        this.username = requireValue(username, "DB_USER");
        this.password = password == null ? "" : password;
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("DB_URL must use jdbc:mysql://");
        }
    }

    public static ConnectionFactory fromEnvironment() {
        return new ConnectionFactory(
                System.getenv("DB_URL"),
                System.getenv("DB_USER"),
                System.getenv("DB_PASSWORD"));
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured in the environment");
        }
        return value.trim();
    }
}