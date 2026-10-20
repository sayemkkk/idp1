package com.reviewapp.server;

import com.reviewapp.config.AppConfig;
import com.reviewapp.config.Database;
import com.reviewapp.dao.FormDao;
import com.reviewapp.dao.QuestionDao;
import com.reviewapp.dao.ResponseDao;
import com.reviewapp.dao.SchemaInitializer;
import com.reviewapp.dao.SessionDao;
import com.reviewapp.dao.UserDao;
import com.reviewapp.handler.AdminHandler;
import com.reviewapp.handler.AuthHandler;
import com.reviewapp.handler.FormHandler;
import com.reviewapp.handler.PublicReviewHandler;
import com.reviewapp.handler.StaticFileHandler;
import com.reviewapp.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Application bootstrap and request router. Wires the DAO layer, services and
 * handlers onto the JDK {@link HttpServer}, mapping each URL prefix to the
 * appropriate handler. This is the single place that knows the URL layout.
 */
public final class WebServer {

    private final HttpServer server;
    private final Services services;
    private final ScheduledExecutorService housekeeping;

    public WebServer() throws Exception {
        Database database = new Database(AppConfig.DB_PATH);
        new SchemaInitializer(database).initialize();

        UserDao userDao = new UserDao(database);
        SessionDao sessionDao = new SessionDao(database);
        FormDao formDao = new FormDao(database);
        QuestionDao questionDao = new QuestionDao(database);
        ResponseDao responseDao = new ResponseDao(database);

        this.services = new Services(sessionDao, userDao);

        this.server = HttpServer.create(new InetSocketAddress(AppConfig.PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors() * 2)));

        // --- JSON API routes ---------------------------------------------
        mount("/api/auth/login", new AuthHandler(services, "login"));
        mount("/api/auth/logout", new AuthHandler(services, "logout"));
        mount("/api/auth/me", new AuthHandler(services, "me"));

        mount("/api/admin", new AdminHandler(services));
        mount("/api/forms", new FormHandler(services, formDao, questionDao, responseDao));
        mount("/api/public/forms", new PublicReviewHandler(services, formDao, questionDao, responseDao));

        // A friendly root API description so the API surface is discoverable.
        mount("/api", apiIndex());

        // --- Static frontend ---------------------------------------------
        // Static handler is the catch-all so SPA routes resolve to index.html.
        server.createContext("/", new StaticFileHandler(Path.of(AppConfig.PUBLIC_DIR)));

        // Periodic cleanup of expired sessions (every 30 minutes).
        this.housekeeping = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-housekeeper");
            t.setDaemon(true);
            return t;
        });
        housekeeping.scheduleAtFixedRate(this::purgeExpiredSessions, 30, 30, TimeUnit.MINUTES);
    }

    private void mount(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    private HttpHandler apiIndex() {
        return (HttpExchange exchange) -> {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("name", "Anonymous Student Review API");
            body.put("endpoints", java.util.List.of(
                    "POST /api/auth/login",
                    "POST /api/auth/logout",
                    "GET  /api/auth/me",
                    "GET  /api/admin/users",
                    "POST /api/admin/users",
                    "PUT  /api/admin/users/{id}",
                    "DELETE /api/admin/users/{id}",
                    "GET  /api/forms",
                    "POST /api/forms",
                    "GET  /api/forms/{id}",
                    "PUT  /api/forms/{id}",
                    "DELETE /api/forms/{id}",
                    "GET  /api/forms/{id}/analytics",
                    "GET  /api/public/forms/{token}",
                    "POST /api/public/forms/{token}/submit"));
            HttpUtil.writeJson(exchange, 200, body);
            exchange.close();
        };
    }

    private void purgeExpiredSessions() {
        try {
            int removed = services.sessions().deleteExpired(java.time.Instant.now().toString());
            if (removed > 0) {
                System.out.println("[housekeeping] purged " + removed + " expired session(s)");
            }
        } catch (Exception e) {
            System.err.println("[housekeeping] failed to purge sessions: " + e.getMessage());
        }
    }

    public void start() {
        server.start();
        System.out.println("====================================================");
        System.out.println(" Anonymous Student Review System is running");
        System.out.println(" Base URL : " + AppConfig.BASE_URL);
        System.out.println(" Database : " + AppConfig.DB_PATH);
        System.out.println(" Default admin login: admin / admin123");
        System.out.println("====================================================");
    }

    public void stop() {
        housekeeping.shutdownNow();
        server.stop(0);
    }
}
