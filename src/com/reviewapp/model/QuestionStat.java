package com.reviewapp.model;

/**
 * An aggregate of a single 1..5 rating question, used by the analytics
 * dashboard. Exposes the question text, average and response count.
 */
public record QuestionStat(long questionId, String text, double average, long count) {
}
