package com.reviewapp.handler;

import com.reviewapp.dao.FormDao;
import com.reviewapp.dao.QuestionDao;
import com.reviewapp.dao.ResponseDao;
import com.reviewapp.model.Form;
import com.reviewapp.model.Question;
import com.reviewapp.server.ApiException;
import com.reviewapp.server.BaseApiHandler;
import com.reviewapp.server.Services;
import com.reviewapp.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public, unauthenticated endpoints for students:
 * <ul>
 *   <li>{@code GET  /api/public/forms/{token}} — fetch form metadata + questions.</li>
 *   <li>{@code POST /api/public/forms/{token}/submit} — submit anonymous answers.</li>
 * </ul>
 * These endpoints intentionally record no student identity or request metadata.
 */
public final class PublicReviewHandler extends BaseApiHandler {

    private final FormDao formDao;
    private final QuestionDao questionDao;
    private final ResponseDao responseDao;

    public PublicReviewHandler(Services services, FormDao formDao,
                               QuestionDao questionDao, ResponseDao responseDao) {
        super(services);
        this.formDao = formDao;
        this.questionDao = questionDao;
        this.responseDao = responseDao;
    }

    @Override
    protected void dispatch(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath();
        String remainder = path.substring("/api/public/forms".length());
        if (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }
        if (remainder.isEmpty()) {
            throw ApiException.notFound("Form token required");
        }

        String[] parts = remainder.split("/");
        String token = parts[0];

        if (parts.length == 1) {
            if (!"GET".equals(method(exchange))) {
                throw new ApiException(405, "Method not allowed");
            }
            getForm(exchange, token);
        } else if (parts.length == 2 && parts[1].equals("submit")) {
            if (!"POST".equals(method(exchange))) {
                throw new ApiException(405, "Method not allowed");
            }
            submit(exchange, token);
        } else {
            throw ApiException.notFound("Unknown public endpoint");
        }
    }

    private void getForm(HttpExchange exchange, String token) throws Exception {
        Form form = formDao.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("This review link is invalid"));
        if (!form.open()) {
            throw new ApiException(410, "This review is closed and no longer accepting responses");
        }

        List<Question> questions = questionDao.findByForm(form.id());
        List<Map<String, Object>> questionMaps = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.id());
            qm.put("orderIndex", q.orderIndex());
            qm.put("text", q.text());
            questionMaps.add(qm);
        }

        Map<String, Object> formMap = new LinkedHashMap<>();
        formMap.put("teacherName", form.teacherName());
        formMap.put("courseName", form.courseName());
        formMap.put("title", form.title());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("form", formMap);
        payload.put("questions", questionMaps);
        HttpUtil.writeJson(exchange, 200, payload);
    }

    private void submit(HttpExchange exchange, String token) throws Exception {
        Form form = formDao.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("This review link is invalid"));
        if (!form.open()) {
            throw new ApiException(410, "This review is closed and no longer accepting responses");
        }

        List<Question> questions = questionDao.findByForm(form.id());
        if (questions.isEmpty()) {
            throw ApiException.badRequest("This form has no questions");
        }
        List<Long> validIds = new ArrayList<>();
        for (Question q : questions) {
            validIds.add(q.id());
        }

        Map<String, Object> body = readJsonObject(exchange);

        // Answers arrive as an object keyed by question id: {"12": 4, "13": 5}
        Object answersRaw = body.get("answers");
        if (!(answersRaw instanceof Map<?, ?> answersMap)) {
            throw ApiException.badRequest("Field 'answers' must be an object of questionId -> score");
        }

        Map<Long, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : answersMap.entrySet()) {
            long questionId;
            try {
                questionId = Long.parseLong(String.valueOf(entry.getKey()));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Invalid question id: " + entry.getKey());
            }
            if (!validIds.contains(questionId)) {
                throw ApiException.badRequest("Question " + questionId + " does not belong to this form");
            }
            Object scoreVal = entry.getValue();
            int score;
            if (scoreVal instanceof Number n) {
                score = n.intValue();
            } else {
                throw ApiException.badRequest("Score for question " + questionId + " must be a number");
            }
            if (score < 1 || score > 5) {
                throw ApiException.badRequest("Score for question " + questionId + " must be between 1 and 5");
            }
            scores.put(questionId, score);
        }

        // Every question must be answered so averages are comparable.
        if (scores.size() != validIds.size()) {
            throw ApiException.badRequest("All questions must be answered");
        }

        String comment = optionalString(body, "comment");
        if (comment != null && comment.length() > 4000) {
            throw ApiException.badRequest("Comment is too long (maximum 4000 characters)");
        }

        responseDao.submit(form.id(), scores, comment);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("message", "Thank you for your anonymous feedback");
        HttpUtil.writeJson(exchange, 201, payload);
    }
}
