package com.reviewapp.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Small helper utilities over {@link HttpExchange}: reading request bodies,
 * parsing cookies and query parameters, and writing JSON or static content.
 */
public final class HttpUtil {

    private static final int MAX_BODY_BYTES = 1 << 20; // 1 MiB

    private HttpUtil() {
    }

    /** Reads the entire request body as UTF-8, guarding against oversized payloads. */
    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("Request body exceeds maximum allowed size");
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** Returns all cookies sent with the request as a name -> value map. */
    public static Map<String, String> cookies(HttpExchange exchange) {
        Map<String, String> result = new LinkedHashMap<>();
        Headers headers = exchange.getRequestHeaders();
        for (String header : headers.getOrDefault("Cookie", java.util.List.of())) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    result.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
                }
            }
        }
        return result;
    }

    public static Optional<String> cookie(HttpExchange exchange, String name) {
        return Optional.ofNullable(cookies(exchange).get(name));
    }

    /** Parses the query string (after '?') into a decoded map. */
    public static Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                String key = urlDecode(pair.substring(0, eq));
                String value = urlDecode(pair.substring(eq + 1));
                params.put(key, value);
            } else {
                params.put(urlDecode(pair), "");
            }
        }
        return params;
    }

    public static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------
    // Responses
    // ---------------------------------------------------------------------

    /** Adds permissive-but-safe CORS headers for the JSON API. */
    public static void addCors(HttpExchange exchange) {
        Headers h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    /** Writes a JSON response with the given HTTP status. */
    public static void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        addCors(exchange);
        writeBytes(exchange, status, bytes);
    }

    public static void writeJson(HttpExchange exchange, int status, Object payload, Headers extra) throws IOException {
        extra.forEach((k, v) -> exchange.getResponseHeaders().put(k, v));
        writeJson(exchange, status, payload);
    }

    /** Writes a plain-text response (rarely used; mostly for errors). */
    public static void writeText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        writeBytes(exchange, status, bytes);
    }

    public static void writeBytes(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /** Convenience for a JSON {@code {"error": "...", "status": N}} error body. */
    public static void writeError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("status", status);
        writeJson(exchange, status, body);
    }
}
