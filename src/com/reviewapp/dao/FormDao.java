package com.reviewapp.dao;

import com.reviewapp.config.Database;
import com.reviewapp.model.Form;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data access for review forms (campaigns). */
public final class FormDao {

    private final Database database;

    public FormDao(Database database) {
        this.database = database;
    }

    public long create(long hodId, String teacherName, String courseName,
                       String title, String token) throws SQLException {
        String sql = """
                INSERT INTO forms (hod_id, teacher_name, course_name, title, token, is_open, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, hodId);
            ps.setString(2, teacherName);
            ps.setString(3, courseName);
            ps.setString(4, title);
            ps.setString(5, token);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create form, no id obtained");
    }

    public Optional<Form> findById(long id) throws SQLException {
        String sql = "SELECT * FROM forms WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Form> findByToken(String token) throws SQLException {
        String sql = "SELECT * FROM forms WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Returns all forms owned by an HOD, newest first. */
    public List<Form> findByHod(long hodId) throws SQLException {
        String sql = "SELECT * FROM forms WHERE hod_id = ? ORDER BY created_at DESC";
        List<Form> forms = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, hodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    forms.add(map(rs));
                }
            }
        }
        return forms;
    }

    public List<Form> findAll() throws SQLException {
        String sql = "SELECT * FROM forms ORDER BY created_at DESC";
        List<Form> forms = new ArrayList<>();
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                forms.add(map(rs));
            }
        }
        return forms;
    }

    public boolean setOpen(long formId, long hodId, boolean open) throws SQLException {
        String sql = "UPDATE forms SET is_open = ? WHERE id = ? AND hod_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, open ? 1 : 0);
            ps.setLong(2, formId);
            ps.setLong(3, hodId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(long formId, long hodId) throws SQLException {
        String sql = "DELETE FROM forms WHERE id = ? AND hod_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            ps.setLong(2, hodId);
            return ps.executeUpdate() > 0;
        }
    }

    public int countResponses(long formId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM responses WHERE form_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Form map(ResultSet rs) throws SQLException {
        return new Form(
                rs.getLong("id"),
                rs.getLong("hod_id"),
                rs.getString("teacher_name"),
                rs.getString("course_name"),
                rs.getString("title"),
                rs.getString("token"),
                rs.getInt("is_open") == 1,
                UserDao.parseInstant(rs.getString("created_at")));
    }
}
