package com.reviewapp.handler;

import com.reviewapp.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves static frontend assets.
 *
 * <p>Assets are resolved in two ways, so the exact same handler works whether
 * you run from the source tree (a {@code public/} directory on disk) or from
 * the packaged executable JAR (where assets live on the classpath under
 * {@code /public/}). The filesystem is checked first; if the file is absent
 * there, the classpath is used. Unknown paths fall back to {@code index.html}
 * to support client-side routes, and directory traversal outside the root is
 * prevented.</p>
 */
public final class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("txt", "text/plain; charset=utf-8"));

    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !head) {
                HttpUtil.writeText(exchange, 405, "Method not allowed");
                return;
            }

            String uriPath = exchange.getRequestURI().getPath();
            String relative = (uriPath.equals("/") || uriPath.isEmpty()) ? "index.html" : uriPath.substring(1);

            Asset asset = resolve(relative);
            if (asset == null) {
                // Fall back to index.html for client-side routes.
                asset = resolve("index.html");
            }
            if (asset == null) {
                HttpUtil.writeText(exchange, 404, "Not found");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", contentType(asset.name));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            if (head) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, asset.bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(asset.bytes);
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * Returns the asset for a relative path, or {@code null} if it does not
     * exist on disk or on the classpath.
     */
    private Asset resolve(String relative) {
        // 1) Filesystem (development mode: ./public)
        try {
            Path resolved = root.resolve(relative).normalize();
            if (resolved.startsWith(root) && Files.isRegularFile(resolved)) {
                return new Asset(resolved.getFileName().toString(), Files.readAllBytes(resolved));
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through to classpath
        }

        // 2) Classpath (packaged JAR: /public)
        String resource = "/public/" + relative;
        try (InputStream in = StaticFileHandler.class.getResourceAsStream(resource)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                String name = relative.contains("/")
                        ? relative.substring(relative.lastIndexOf('/') + 1)
                        : relative;
                return new Asset(name, bytes);
            }
        } catch (IOException ignored) {
            // treat as not found
        }
        return null;
    }

    private String contentType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private record Asset(String name, byte[] bytes) {
    }
}
