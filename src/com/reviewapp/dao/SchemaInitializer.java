package com.reviewapp.dao;

import com.reviewapp.config.Database;
import com.reviewapp.model.Role;
import com.reviewapp.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the database schema on first run and seeds a default super admin
 * account so the system is usable immediately after a fresh start.
 */
public final class SchemaInitializer {

    private final Database database;

    public SchemaInitializer(Database database) {
        this.database = database;
    }

    public void initialize() throws SQLException {
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        username      TEXT    NOT NULL UNIQUE,
                        password_hash TEXT    NOT NULL,
                        role          TEXT    NOT NULL CHECK (role IN ('super_admin','hod')),
                        department    TEXT,
                        full_name     TEXT,
                        active        INTEGER NOT NULL DEFAULT 1,
                        created_at    TEXT    NOT NULL
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS forms (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        hod_id       INTEGER NOT NULL,
                        teacher_name TEXT    NOT NULL,
                        course_name  TEXT    NOT NULL,
                        title        TEXT,
                        token        TEXT    NOT NULL UNIQUE,
                        is_open      INTEGER NOT NULL DEFAULT 1,
                        created_at   TEXT    NOT NULL,
                        FOREIGN KEY (hod_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS questions (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        form_id     INTEGER NOT NULL,
                        order_index INTEGER NOT NULL,
                        text        TEXT    NOT NULL,
                        FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE
                    )
                    """);

            // Responses deliberately store NO student identifier. The only data
            // retained is the form reference, the ratings and the comment.
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS responses (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        form_id     INTEGER NOT NULL,
                        comment     TEXT,
                        submitted_at TEXT   NOT NULL,
                        FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS answers (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        response_id INTEGER NOT NULL,
                        question_id INTEGER NOT NULL,
                        score       INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
                        FOREIGN KEY (response_id) REFERENCES responses(id) ON DELETE CASCADE,
                        FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id         TEXT    PRIMARY KEY,
                        user_id    INTEGER NOT NULL,
                        role       TEXT    NOT NULL,
                        expires_at TEXT    NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_questions_form ON questions(form_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_responses_form ON responses(form_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_answers_response ON answers(response_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_answers_question ON answers(question_id)");

            seedSuperAdmin(conn);
        }
    }

    private void seedSuperAdmin(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, Role.SUPER_ADMIN.toDb());
            try (var rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) > 0) {
                    return;
                }
            }
        }

        String insert = """
                INSERT INTO users (username, password_hash, role, department, full_name, active, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        try (var ps = conn.prepareStatement(insert)) {
            ps.setString(1, "admin");
            ps.setString(2, PasswordUtil.hash("admin123"));
            ps.setString(3, Role.SUPER_ADMIN.toDb());
            ps.setString(4, null);
            ps.setString(5, "System Administrator");
            ps.setString(6, java.time.Instant.now().toString());
            ps.executeUpdate();
        }
    }
}
