package com.reviewapp.model;

/**
 * A review question belonging to a form. Each question is answered on a 1..5
 * Likert scale. The behaviour aspects are phrased in the seed forms; HODs may
 * define their own prompts.
 */
public record Question(long id, long formId, int orderIndex, String text) {
}
