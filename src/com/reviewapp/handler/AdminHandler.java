package com.reviewapp.handler;

import com.reviewapp.model.Role;
import com.reviewapp.model.User;
import com.reviewapp.server.ApiException;
import com.reviewapp.server.BaseApiHandler;
import com.reviewapp.server.Services;
import com.reviewapp.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Super-admin account management endpoints under {@code /api/admin/users}:
 * <ul>
 *   <li>{@code GET    /api/admin/users} — list HOD (and admin) accounts.</li>
 *   <li>{@code POST   /api/admin/users} — create a new account.</li>
 *   <li>{@code PUT    /api/admin/users/{id}} — enable/disable or reset password.</li>
 *   <li>{@code DELETE /api/admin/users/{id}} — remove an HOD account.</li>
 *   <li>{@code GET    /api/admin/overview} — platform-wide counts.</li>
 * </ul>
 */
public final class AdminHandler extends BaseApiHandler {

    public AdminHandler(Services services) {
        super(services);
    }

    @Override
    protected void dispatch(HttpExchange exchange) throws Exception {
        // Only super admins may reach any admin endpoint.
        requireRole(exchange, Role.SUPER_ADMIN);

        String path = exchange.getRequestURI().getPath();
        String remainder = path.substring("/api/admin".length());
        if (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }

        if (remainder.equals("users") || remainder.equals("users/")) {
            handleCollection(exchange);
        } else if (remainder.startsWith("users/")) {
            handleItem(exchange, remainder.substring("users/".length()));
        } else if (remainder.equals("overview")) {
            overview(exchange);
        } else {
            throw ApiException.notFound("Unknown admin endpoint");
        }
    }

    private void handleCollection(HttpExchange exchange) throws Exception {
        if ("GET".equals(method(exchange))) {
            listUsers(exchange);
        } else if ("POST".equals(method(exchange))) {
            createUser(exchange);
        } else {
            throw new ApiException(405, "Method not allowed");
        }
    }

    private void listUsers(HttpExchange exchange) throws Exception {
        List<User> users = services.users().findAll().stream()
                .filter(u -> u.role() != Role.SUPER_ADMIN)
                .collect(Collectors.toList());
        List<Map<String, Object>> payload = users.stream()
                .map(User::toPublicMap)
                .collect(Collectors.toList());
        HttpUtil.writeJson(exchange, 200, Map.of("users", payload));
    }

    private void createUser(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readJsonObject(exchange);
        String username = requireString(body, "username");
        String password = requireString(body, "password");
        String roleStr = requireString(body, "role").toUpperCase();
        String department = optionalString(body, "department");
        String fullName = optionalString(body, "fullName");

        if (username.length() < 3) {
            throw ApiException.badRequest("Username must be at least 3 characters");
        }
        if (password.length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }
        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid role: " + roleStr);
        }
        if (role == Role.SUPER_ADMIN) {
            throw ApiException.badRequest("Cannot create additional super admins via API");
        }
        if (services.users().usernameExists(username)) {
            throw ApiException.conflict("Username already exists");
        }

        long id = services.users().create(username, password, role, department, fullName);
        User created = services.users().findById(id)
                .orElseThrow(() -> new IllegalStateException("Created user not found"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", created.toPublicMap());
        HttpUtil.writeJson(exchange, 201, response);
    }

    private void handleItem(HttpExchange exchange, String idPart) throws Exception {
        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid user id");
        }

        switch (method(exchange)) {
            case "PUT" -> updateUser(exchange, id);
            case "DELETE" -> deleteUser(exchange, id);
            default -> throw new ApiException(405, "Method not allowed");
        }
    }

    private void updateUser(HttpExchange exchange, long id) throws Exception {
        User target = services.users().findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.role() == Role.SUPER_ADMIN) {
            throw ApiException.forbidden("Cannot modify the super admin account");
        }

        Map<String, Object> body = readJsonObject(exchange);
        boolean changed = false;

        Object activeVal = body.get("active");
        if (activeVal instanceof Boolean b) {
            services.users().setActive(id, b);
            changed = true;
        }
        String password = optionalString(body, "password");
        if (password != null) {
            if (password.length() < 6) {
                throw ApiException.badRequest("Password must be at least 6 characters");
            }
            services.users().updatePassword(id, password);
            changed = true;
        }
        if (!changed) {
            throw ApiException.badRequest("No supported fields provided (active/password)");
        }

        User updated = services.users().findById(id)
                .orElseThrow(() -> new IllegalStateException("Updated user not found"));
        HttpUtil.writeJson(exchange, 200, Map.of("user", updated.toPublicMap()));
    }

    private void deleteUser(HttpExchange exchange, long id) throws Exception {
        User target = services.users().findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.role() == Role.SUPER_ADMIN) {
            throw ApiException.forbidden("Cannot delete the super admin account");
        }
        boolean deleted = services.users().delete(id);
        if (!deleted) {
            throw ApiException.notFound("User not found");
        }
        HttpUtil.writeJson(exchange, 200, Map.of("ok", true));
    }

    private void overview(HttpExchange exchange) throws Exception {
        if (!"GET".equals(method(exchange))) {
            throw new ApiException(405, "Method not allowed");
        }
        List<User> all = services.users().findAll();
        long hodCount = all.stream().filter(u -> u.role() == Role.HOD).count();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hods", hodCount);
        payload.put("superAdmins", all.stream().filter(u -> u.role() == Role.SUPER_ADMIN).count());
        HttpUtil.writeJson(exchange, 200, payload);
    }
}
