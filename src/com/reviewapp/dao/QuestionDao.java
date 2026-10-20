package com.reviewapp.dao;

import com.reviewapp.config.Database;
import com.reviewapp.model.Question;
import com.reviewapp.model.QuestionStat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data access for review questions and per-question analytics. */
public final class QuestionDao {

    private final Database database;

    public QuestionDao(Database database) {
        this.database = database;
    }

    public void create(long formId, int orderIndex, String text) throws SQLException {
        String sql = "INSERT INTO questions (form_id, order_index, text) VALUES (?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            ps.setInt(2, orderIndex);
            ps.setString(3, text);
            ps.executeUpdate();
        }
    }

    public List<Question> findByForm(long formId) throws SQLException {
        String sql = "SELECT * FROM questions WHERE form_id = ? ORDER BY order_index";
        List<Question> questions = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(new Question(
                            rs.getLong("id"),
                            rs.getLong("form_id"),
                            rs.getInt("order_index"),
                            rs.getString("text")));
                }
            }
        }
        return questions;
    }

    /**
     * Computes the average score and response count for every question in a
     * form. Uses a LEFT JOIN so questions with no answers still appear (with a
     * zero average and zero count).
     */
    public List<QuestionStat> computeStats(long formId) throws SQLException {
        String sql = """
                SELECT q.id, q.text, COALESCE(AVG(a.score), 0) AS avg_score, COUNT(a.id) AS cnt
                FROM questions q
                LEFT JOIN answers a ON a.question_id = q.id
                WHERE q.form_id = ?
                GROUP BY q.id, q.text, q.order_index
                ORDER BY q.order_index
                """;
        List<QuestionStat> stats = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.add(new QuestionStat(
                            rs.getLong("id"),
                            rs.getString("text"),
                            rs.getDouble("avg_score"),
                            rs.getLong("cnt")));
                }
            }
        }
        return stats;
    }

    /**
     * The overall average across all answers for a form, or 0 when no answers
     * exist yet.
     */
    public double overallAverage(long formId) throws SQLException {
        String sql = """
                SELECT COALESCE(AVG(a.score), 0)
                FROM answers a
                JOIN questions q ON q.id = a.question_id
                WHERE q.form_id = ?
                """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }
}
