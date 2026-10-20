package com.reviewapp.model;

import java.time.Instant;

/**
 * A review campaign created by an HOD for a specific teacher and course.
 * Each form has a unique public token that yields the anonymous submission
 * link {@code /review.html?token=...}.
 */
public record Form(
        long id,
        long hodId,
        String teacherName,
        String courseName,
        String title,
        String token,
        boolean open,
        Instant createdAt) {

    public java.util.Map<String, Object> toMap(boolean includeToken) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("hodId", hodId);
        m.put("teacherName", teacherName);
        m.put("courseName", courseName);
        m.put("title", title);
        m.put("open", open);
        m.put("createdAt", createdAt == null ? null : createdAt.toString());
        if (includeToken) {
            m.put("token", token);
        }
        return m;
    }
}
