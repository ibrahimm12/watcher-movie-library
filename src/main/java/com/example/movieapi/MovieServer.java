package com.example.movieapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

public final class MovieServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 1_000_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> STATIC_ASSETS = Map.of(
            "/", "/public/index.html",
            "/index.html", "/public/index.html",
            "/assets/app.js", "/public/assets/app.js",
            "/assets/app.css", "/public/assets/app.css");

    private final HttpServer server;
    private final MovieStore movieStore;

    public MovieServer(Path dataFile, int port) throws IOException {
        this(new PostgresMovieRepository(
                System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/movies"),
                System.getenv().getOrDefault("DB_USER", "movies"),
                requiredEnvironment("DB_PASSWORD"),
                dataFile), port);
    }

    MovieServer(MovieStore movieStore, int port) throws IOException {
        this.movieStore = movieStore;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/movies", this::handleMovies);
        server.createContext("/health/", this::handleHealth);
        server.createContext("/", this::handleStatic);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path dataFile = Path.of(System.getProperty("movies.file", "movies-compact.json"));
        MovieServer movieServer = new MovieServer(dataFile, port);
        Runtime.getRuntime().addShutdownHook(new Thread(movieServer::close));
        movieServer.start();
        System.out.printf("Movie API listening at http://localhost:%d/api/movies%n", movieServer.port());
    }

    private static String requiredEnvironment(String name) throws IOException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IOException("Environment variable " + name + " must be set");
        }
        return value;
    }

    private void handleMovies(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals("/api/movies")) {
                sendError(exchange, 404, "Endpoint not found");
                return;
            }

            String method = exchange.getRequestMethod();
            if (method.equals("GET")) {
                handleList(exchange);
            } else if (method.equals("POST")) {
                handleAdd(exchange);
            } else {
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                sendError(exchange, 405, "Method not allowed");
            }
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/health/live")) {
                sendJson(exchange, 200, JSON.createObjectNode().put("status", "UP"));
            } else if (path.equals("/health/ready")) {
                try {
                    boolean ready = movieStore.isReady();
                    sendJson(exchange, ready ? 200 : 503,
                            JSON.createObjectNode().put("status", ready ? "UP" : "DOWN"));
                } catch (IOException exception) {
                    sendJson(exchange, 503, JSON.createObjectNode().put("status", "DOWN"));
                }
            } else {
                sendError(exchange, 404, "Health endpoint not found");
            }
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String resource = STATIC_ASSETS.get(exchange.getRequestURI().getPath());
            if (resource == null) {
                sendError(exchange, 404, "Page not found");
                return;
            }

            try (var content = MovieServer.class.getResourceAsStream(resource)) {
                if (content == null) {
                    sendError(exchange, 404, "Page not found");
                    return;
                }

                String contentType = resource.endsWith(".css") ? "text/css; charset=utf-8"
                        : resource.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
                byte[] bytes = content.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.getResponseHeaders().set("Content-Security-Policy",
                        "default-src 'self'; style-src 'self' https://fonts.googleapis.com; "
                                + "font-src https://fonts.gstatic.com; script-src 'self'; img-src 'self' data:; "
                                + "connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        String searchTerm = queryParameter(exchange.getRequestURI().getRawQuery(), "q");
        try {
            sendJson(exchange, 200, movieStore.find(searchTerm));
        } catch (IOException exception) {
            sendError(exchange, 500, "Could not read movies");
        }
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            sendError(exchange, 413, "Request body is too large");
            return;
        }

        JsonNode movie;
        try {
            movie = JSON.readTree(body);
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Request body must contain valid JSON");
            return;
        }

        if (movie == null || !movie.isObject() || !movie.path("name").isTextual()
                || movie.path("name").asText().isBlank()) {
            sendError(exchange, 400, "Movie must be a JSON object with a non-empty name");
            return;
        }

        try {
            sendJson(exchange, 201, movieStore.add(movie));
        } catch (IOException exception) {
            sendError(exchange, 500, "Could not save the movie");
        }
    }

    private static String queryParameter(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return "";
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        ObjectNode error = JSON.createObjectNode().put("error", message);
        sendJson(exchange, status, error);
    }

    private static void sendJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

}