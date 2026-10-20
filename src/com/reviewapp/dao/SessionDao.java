package com.reviewapp.dao;

import com.reviewapp.config.Database;
import com.reviewapp.model.Role;
import com.reviewapp.model.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Persists login sessions in SQLite. */
public final class SessionDao {

    private final Database database;

    public SessionDao(Database database) {
        this.database = database;
    }

    public void create(Session session) throws SQLException {
        String sql = "INSERT INTO sessions (id, user_id, role, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.id());
            ps.setLong(2, session.userId());
            ps.setString(3, session.role().toDb());
            ps.setString(4, session.expiresAt().toString());
            ps.executeUpdate();
        }
    }

    public Optional<Session> find(String id) throws SQLException {
        String sql = "SELECT * FROM sessions WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Session(
                            rs.getString("id"),
                            rs.getLong("user_id"),
                            Role.fromDb(rs.getString("role")),
                            Instant.parse(rs.getString("expires_at"))));
                }
            }
        }
        return Optional.empty();
    }

    public void delete(String id) throws SQLException {
        String sql = "DELETE FROM sessions WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    /** Removes all expired rows; called periodically to keep the table clean. */
    public int deleteExpired(String nowIso) throws SQLException {
        String sql = "DELETE FROM sessions WHERE expires_at <= ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nowIso);
            return ps.executeUpdate();
        }
    }
}
