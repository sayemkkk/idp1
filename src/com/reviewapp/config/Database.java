package com.reviewapp.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Supplies JDBC connections to the SQLite database. SQLite is embedded, so no
 * connection pooling is required; a new short-lived connection is opened per
 * operation and closed via try-with-resources in the DAO layer.
 *
 * <p>SQLite allows only one writer at a time, so we enable foreign keys,
 * a busy timeout and WAL journaling for better concurrent read behaviour.</p>
 */
public final class Database {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "SQLite JDBC driver not found on classpath. Add sqlite-jdbc JAR to -cp.", e);
        }
    }

    private final String url;

    public Database(String dbPath) {
        this.url = JDBC_PREFIX + dbPath;
    }

    /** Opens a new connection configured with sensible SQLite pragmas. */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (var st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }
}
