package edu.lab;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class FeedbackServer {
    private static final int MAX_BODY = 16_384;
    private static final Set<String> FIELDS = Set.of("name", "email", "category", "rating", "comments");
    private static final Set<String> CATEGORIES = Set.of("General", "Suggestion", "Issue", "Appreciation");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+");

    private FeedbackServer() { }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        HttpServer server = createServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.println("Online Feedback Form listening on port " + port);
    }

    static HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 32);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", FeedbackServer::handle);
        return server;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            int port = exchange.getLocalAddress().getPort();
            if (!("localhost:" + port).equals(host) && !("127.0.0.1:" + port).equals(host)) {
                respond(exchange, 403, "text/plain", "Use localhost to access this lab.\n");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/feedback".equals(path)) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    respond(exchange, 405, "text/plain", "Use the feedback form to submit.\n");
                } else {
                    submit(exchange, host);
                }
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                respond(exchange, 405, "text/plain", "Method not allowed.\n");
                return;
            }
            switch (path) {
                case "/", "/index.html" -> resource(exchange, "/index.html", "text/html");
                case "/style.css" -> resource(exchange, "/style.css", "text/css");
                case "/tokens.css" -> resource(exchange, "/tokens.css", "text/css");
                case "/health" -> respond(exchange, 200, "text/plain", "OK\n");
                default -> respond(exchange, 404, "text/plain", "Page not found.\n");
            }
        }
    }

    private static void submit(HttpExchange exchange, String host) throws IOException {
        // Exact local origin prevents cross-site form posts and DNS rebinding; no cookies or sessions.
        if (!("http://" + host).equals(exchange.getRequestHeaders().getFirst("Origin"))) {
            respond(exchange, 403, "text/plain", "Open the form on localhost and submit again.\n");
            return;
        }
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            respond(exchange, 415, "text/plain", "Submit a URL-encoded form.\n");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            respond(exchange, 413, "text/plain", "Feedback is too large. Limit comments to 2000 characters.\n");
            return;
        }
        try {
            Map<String, String> values = parseForm(new String(body, StandardCharsets.UTF_8));
            if (!valid(values)) {
                respond(exchange, 400, "text/plain", "Check all fields: name 1-100, valid email up to 254, "
                        + "listed category, rating 1-5, comments 1-2000 characters. Use Back to edit.\n");
                return;
            }
            // This demonstration deliberately confirms without storing or logging personal information.
            resource(exchange, "/success.html", "text/html");
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "text/plain", "Invalid form data. Use Back to edit and try again.\n");
        }
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Missing field value");
            }
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8).strip();
            if (!FIELDS.contains(key) || values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Unknown or duplicate field");
            }
        }
        return values;
    }

    static boolean valid(Map<String, String> values) {
        return values.keySet().equals(FIELDS)
                && !values.get("name").isBlank() && values.get("name").length() <= 100
                && values.get("email").length() <= 254 && EMAIL.matcher(values.get("email")).matches()
                && CATEGORIES.contains(values.get("category"))
                && Set.of("1", "2", "3", "4", "5").contains(values.get("rating"))
                && !values.get("comments").isBlank() && values.get("comments").length() <= 2000;
    }

    private static void resource(HttpExchange exchange, String path, String type) throws IOException {
        try (var stream = FeedbackServer.class.getResourceAsStream(path)) {
            if (stream == null) {
                respond(exchange, 500, "text/plain", "Application resource unavailable.\n");
                return;
            }
            respond(exchange, 200, type, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void respond(HttpExchange exchange, int status, String type, String text) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type + "; charset=UTF-8");
        headers.set("Content-Security-Policy", "default-src 'none'; style-src 'self'; "
                + "form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "same-origin");
        headers.set("Cache-Control", "no-store");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
