package com.reviewapp.handler;

import com.reviewapp.config.AppConfig;
import com.reviewapp.model.Session;
import com.reviewapp.model.User;
import com.reviewapp.server.BaseApiHandler;
import com.reviewapp.server.Services;
import com.reviewapp.util.HttpUtil;
import com.reviewapp.util.PasswordUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles authentication endpoints:
 * <ul>
 *   <li>{@code POST /api/auth/login} — authenticate and set a session cookie.</li>
 *   <li>{@code POST /api/auth/logout} — clear the session.</li>
 *   <li>{@code GET  /api/auth/me} — return the current user or 401.</li>
 * </ul>
 */
public final class AuthHandler extends BaseApiHandler {

    private final String path;

    public AuthHandler(Services services, String path) {
        super(services);
        this.path = path;
    }

    @Override
    protected void dispatch(HttpExchange exchange) throws Exception {
        switch (path) {
            case "login" -> login(exchange);
            case "logout" -> logout(exchange);
            case "me" -> me(exchange);
            default -> throw com.reviewapp.server.ApiException.notFound("Unknown auth endpoint");
        }
    }

    private void login(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "POST");
        Map<String, Object> body = readJsonObject(exchange);
        String username = requireString(body, "username");
        String password = requireString(body, "password");

        Optional<User> userOpt;
        try {
            userOpt = services.users().findByUsername(username);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load user", e);
        }

        if (userOpt.isEmpty() || !PasswordUtil.verify(password, userOpt.get().passwordHash())) {
            throw com.reviewapp.server.ApiException.unauthorized("Invalid username or password");
        }
        User user = userOpt.get();
        if (!user.active()) {
            throw com.reviewapp.server.ApiException.forbidden("Account is disabled");
        }

        Session session = services.createSession(user);
        int maxAge = AppConfig.SESSION_TTL_SECONDS;
        Headers headers = new Headers();
        headers.add("Set-Cookie", AppConfig.SESSION_COOKIE + "=" + session.id()
                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", user.toPublicMap());
        HttpUtil.writeJson(exchange, 200, payload, headers);
    }

    private void logout(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "POST");
        services.logout(currentSessionId(exchange));
        Headers headers = new Headers();
        headers.add("Set-Cookie", AppConfig.SESSION_COOKIE
                + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        HttpUtil.writeJson(exchange, 200, payload, headers);
    }

    private void me(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "GET");
        User user = requireUser(exchange);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", user.toPublicMap());
        HttpUtil.writeJson(exchange, 200, payload);
    }
}
