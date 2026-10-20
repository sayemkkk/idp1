package com.reviewapp.dao;

import com.reviewapp.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access for anonymous responses and their answers.
 *
 * <p>No student identity, IP address or user agent is ever persisted here. A
 * submission is stored as a single response row plus one answer row per
 * question, written inside one transaction so a partial submission can never
 * be committed.</p>
 */
public final class ResponseDao {

    private final Database database;

    public ResponseDao(Database database) {
        this.database = database;
    }

    /**
     * Stores a complete anonymous submission atomically.
     *
     * @param formId  the form being answered
     * @param scores  ordered map of questionId -> score (1..5)
     * @param comment free-text optional comment
     * @return the generated response id
     */
    public long submit(long formId, Map<Long, Integer> scores, String comment) throws SQLException {
        String insertResponse = "INSERT INTO responses (form_id, comment, submitted_at) VALUES (?, ?, ?)";
        String insertAnswer = "INSERT INTO answers (response_id, question_id, score) VALUES (?, ?, ?)";

        Connection conn = null;
        try {
            conn = database.getConnection();
            conn.setAutoCommit(false);

            long responseId;
            try (PreparedStatement ps = conn.prepareStatement(insertResponse, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, formId);
                ps.setString(2, comment);
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        conn.rollback();
                        throw new SQLException("Failed to insert response");
                    }
                    responseId = keys.getLong(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertAnswer)) {
                for (Map.Entry<Long, Integer> e : scores.entrySet()) {
                    ps.setLong(1, responseId);
                    ps.setLong(2, e.getKey());
                    ps.setInt(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return responseId;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    // rollback failure is secondary to the original error
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                    // nothing more we can do on close failure
                }
            }
        }
    }

    /**
     * Returns the written feedback for a form, newest first. Each entry carries
     * the timestamp and the optional comment; it never contains who wrote it.
     */
    public List<Map<String, Object>> findComments(long formId) throws SQLException {
        String sql = "SELECT id, comment, submitted_at FROM responses WHERE form_id = ? ORDER BY submitted_at DESC";
        List<Map<String, Object>> comments = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String comment = rs.getString("comment");
                    if (comment == null || comment.isBlank()) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("comment", comment);
                    m.put("submittedAt", rs.getString("submitted_at"));
                    comments.add(m);
                }
            }
        }
        return comments;
    }

    /** Counts rating-bearing responses for a form (used on dashboards). */
    public int countForForm(long formId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM responses WHERE form_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
