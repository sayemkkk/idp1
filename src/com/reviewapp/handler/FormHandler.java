package com.reviewapp.handler;

import com.reviewapp.model.Form;
import com.reviewapp.model.Question;
import com.reviewapp.model.QuestionStat;
import com.reviewapp.model.Role;
import com.reviewapp.model.User;
import com.reviewapp.server.ApiException;
import com.reviewapp.server.BaseApiHandler;
import com.reviewapp.server.Services;
import com.reviewapp.util.HttpUtil;
import com.reviewapp.util.TokenGenerator;
import com.sun.net.httpserver.HttpExchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HOD-facing form management REST endpoints under {@code /api/forms}:
 * <ul>
 *   <li>{@code GET    /api/forms} — list the HOD's forms with response counts.</li>
 *   <li>{@code POST   /api/forms} — create a form + questions, return token/link.</li>
 *   <li>{@code GET    /api/forms/{id}} — form details with questions.</li>
 *   <li>{@code PUT    /api/forms/{id}} — open/close a form.</li>
 *   <li>{@code DELETE /api/forms/{id}} — delete a form.</li>
 *   <li>{@code GET    /api/forms/{id}/analytics} — averages + comments.</li>
 * </ul>
 */
public final class FormHandler extends BaseApiHandler {

    private final com.reviewapp.dao.FormDao formDao;
    private final com.reviewapp.dao.QuestionDao questionDao;
    private final com.reviewapp.dao.ResponseDao responseDao;

    public FormHandler(Services services,
                       com.reviewapp.dao.FormDao formDao,
                       com.reviewapp.dao.QuestionDao questionDao,
                       com.reviewapp.dao.ResponseDao responseDao) {
        super(services);
        this.formDao = formDao;
        this.questionDao = questionDao;
        this.responseDao = responseDao;
    }

    @Override
    protected void dispatch(HttpExchange exchange) throws Exception {
        User user = requireRole(exchange, Role.HOD);

        String path = exchange.getRequestURI().getPath();
        String remainder = path.substring("/api/forms".length());
        if (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }

        if (remainder.isEmpty()) {
            if ("GET".equals(method(exchange))) {
                listForms(exchange, user);
            } else if ("POST".equals(method(exchange))) {
                createForm(exchange, user);
            } else {
                throw new ApiException(405, "Method not allowed");
            }
            return;
        }

        String[] parts = remainder.split("/");
        long formId = parseId(parts[0]);

        if (parts.length == 1) {
            switch (method(exchange)) {
                case "GET" -> getForm(exchange, user, formId);
                case "PUT" -> updateForm(exchange, user, formId);
                case "DELETE" -> deleteForm(exchange, user, formId);
                default -> throw new ApiException(405, "Method not allowed");
            }
        } else if (parts.length == 2 && parts[1].equals("analytics")) {
            if (!"GET".equals(method(exchange))) {
                throw new ApiException(405, "Method not allowed");
            }
            analytics(exchange, user, formId);
        } else {
            throw ApiException.notFound("Unknown form endpoint");
        }
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid form id");
        }
    }

    private Form ownedForm(User user, long formId) throws Exception {
        Form form = formDao.findById(formId)
                .orElseThrow(() -> ApiException.notFound("Form not found"));
        if (form.hodId() != user.id()) {
            throw ApiException.forbidden("You do not own this form");
        }
        return form;
    }

    private void listForms(HttpExchange exchange, User user) throws Exception {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Form form : formDao.findByHod(user.id())) {
            Map<String, Object> m = form.toMap(true);
            m.put("responseCount", formDao.countResponses(form.id()));
            m.put("reviewLink", "/review.html?token=" + form.token());
            payload.add(m);
        }
        HttpUtil.writeJson(exchange, 200, Map.of("forms", payload));
    }

    private void createForm(HttpExchange exchange, User user) throws Exception {
        Map<String, Object> body = readJsonObject(exchange);
        String teacherName = requireString(body, "teacherName");
        String courseName = requireString(body, "courseName");
        String title = optionalString(body, "title");

        Object questionsRaw = body.get("questions");
        List<String> questionTexts = new ArrayList<>();
        if (questionsRaw instanceof List<?> list) {
            for (Object q : list) {
                if (q instanceof String s && !s.isBlank()) {
                    questionTexts.add(s.trim());
                }
            }
        }
        if (questionTexts.isEmpty()) {
            throw ApiException.badRequest("At least one question is required");
        }
        if (questionTexts.size() > 50) {
            throw ApiException.badRequest("A form may contain at most 50 questions");
        }

        String token = TokenGenerator.formToken();
        long formId = formDao.create(user.id(), teacherName, courseName, title, token);
        for (int i = 0; i < questionTexts.size(); i++) {
            questionDao.create(formId, i, questionTexts.get(i));
        }

        Form form = formDao.findById(formId)
                .orElseThrow(() -> new IllegalStateException("Created form not found"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("form", form.toMap(true));
        response.put("reviewLink", "/review.html?token=" + token);
        HttpUtil.writeJson(exchange, 201, response);
    }

    private void getForm(HttpExchange exchange, User user, long formId) throws Exception {
        Form form = ownedForm(user, formId);
        List<Question> questions = questionDao.findByForm(formId);

        List<Map<String, Object>> questionMaps = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.id());
            qm.put("orderIndex", q.orderIndex());
            qm.put("text", q.text());
            questionMaps.add(qm);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("form", form.toMap(true));
        payload.put("questions", questionMaps);
        payload.put("responseCount", formDao.countResponses(formId));
        HttpUtil.writeJson(exchange, 200, payload);
    }

    private void updateForm(HttpExchange exchange, User user, long formId) throws Exception {
        ownedForm(user, formId);
        Map<String, Object> body = readJsonObject(exchange);
        Object openVal = body.get("open");
        if (!(openVal instanceof Boolean open)) {
            throw ApiException.badRequest("Field 'open' (boolean) is required");
        }
        formDao.setOpen(formId, user.id(), open);
        Form form = formDao.findById(formId)
                .orElseThrow(() -> ApiException.notFound("Form not found"));
        HttpUtil.writeJson(exchange, 200, Map.of("form", form.toMap(true)));
    }

    private void deleteForm(HttpExchange exchange, User user, long formId) throws Exception {
        ownedForm(user, formId);
        if (!formDao.delete(formId, user.id())) {
            throw ApiException.notFound("Form not found");
        }
        HttpUtil.writeJson(exchange, 200, Map.of("ok", true));
    }

    private void analytics(HttpExchange exchange, User user, long formId) throws Exception {
        Form form = ownedForm(user, formId);
        List<QuestionStat> stats = questionDao.computeStats(formId);
        double overall = questionDao.overallAverage(formId);
        int responseCount = responseDao.countForForm(formId);

        List<Map<String, Object>> questionStats = new ArrayList<>();
        for (QuestionStat s : stats) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("questionId", s.questionId());
            qm.put("text", s.text());
            qm.put("average", round2(s.average()));
            qm.put("count", s.count());
            questionStats.add(qm);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("form", form.toMap(true));
        payload.put("responseCount", responseCount);
        payload.put("overallAverage", round2(overall));
        payload.put("questionStats", questionStats);
        payload.put("comments", responseDao.findComments(formId));
        HttpUtil.writeJson(exchange, 200, payload);
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
