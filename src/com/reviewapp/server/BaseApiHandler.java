package com.reviewapp.server;

import com.reviewapp.config.AppConfig;
import com.reviewapp.model.Role;
import com.reviewapp.model.User;
import com.reviewapp.util.HttpUtil;
import com.reviewapp.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for JSON API handlers. Provides:
 * <ul>
 *   <li>centralized error handling that maps {@link ApiException} to HTTP codes,</li>
 *   <li>body parsing into a {@code Map<String,Object>},</li>
 *   <li>authentication helpers (current user / role enforcement).</li>
 * </ul>
 */
public abstract class BaseApiHandler implements HttpHandler {

    protected final Services services;

    protected BaseApiHandler(Services services) {
        this.services = services;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            // CORS pre-flight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.addCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            dispatch(exchange);
        } catch (ApiException e) {
            HttpUtil.writeError(exchange, e.getStatus(), e.getMessage());
        } catch (Json.JsonException e) {
            HttpUtil.writeError(exchange, 400, "Malformed JSON: " + e.getMessage());
        } catch (Exception e) {
            // Log server-side; return a generic message to the client.
            System.err.println("[ERROR] " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI() + " -> " + e);
            e.printStackTrace();
            try {
                HttpUtil.writeError(exchange, 500, "Internal server error");
            } catch (IOException ignored) {
                // response may already be partially written
            }
        } finally {
            exchange.close();
        }
    }

    /** Subclasses implement the actual request logic. */
    protected abstract void dispatch(HttpExchange exchange) throws Exception;

    // ---------------------------------------------------------------------
    // Request helpers
    // ---------------------------------------------------------------------

    protected String method(HttpExchange exchange) {
        return exchange.getRequestMethod().toUpperCase();
    }

    protected Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readBody(exchange);
        if (body.isBlank()) {
            throw ApiException.badRequest("Request body is required");
        }
        return Json.parseObject(body);
    }

    protected void requireMethod(HttpExchange exchange, String... allowed) {
        String m = method(exchange);
        for (String a : allowed) {
            if (a.equals(m)) {
                return;
            }
        }
        throw new ApiException(405, "Method " + m + " not allowed");
    }

    protected String currentSessionId(HttpExchange exchange) {
        return HttpUtil.cookie(exchange, AppConfig.SESSION_COOKIE).orElse(null);
    }

    protected Optional<User> optionalUser(HttpExchange exchange) {
        return services.resolveUser(currentSessionId(exchange));
    }

    protected User requireUser(HttpExchange exchange) {
        return optionalUser(exchange)
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    protected User requireRole(HttpExchange exchange, Role... roles) {
        User user = requireUser(exchange);
        services.requireRole(user, roles);
        return user;
    }

    // ---------------------------------------------------------------------
    // Body field helpers
    // ---------------------------------------------------------------------

    protected String requireString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw ApiException.badRequest("Missing required field: " + field);
        }
        return s.trim();
    }

    protected String optionalString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }
}
