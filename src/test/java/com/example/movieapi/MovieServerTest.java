package com.example.movieapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovieServerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private MovieServer server;
    private HttpClient client;
    private TestMovieStore movieStore;

    @BeforeEach
    void startServer() throws Exception {
        movieStore = new TestMovieStore((ArrayNode) JSON.readTree("""
                [
                  {"name":"Arrival","year":2016,"genres":["Drama","Sci-fi"],"rating":4},
                  {"name":"Moonlight","year":2016,"genres":["Drama"],"rating":5}
                ]
            """));
        server = new MovieServer(movieStore, 0);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    @Test
    void listsSearchesAndAddsMovies() throws Exception {
        HttpResponse<String> allMovies = get("/api/movies");
        assertEquals(200, allMovies.statusCode());
        assertEquals(2, JSON.readTree(allMovies.body()).size());

        HttpResponse<String> search = get("/api/movies?q=sci-fi");
        assertEquals(200, search.statusCode());
        JsonNode matches = JSON.readTree(search.body());
        assertEquals(1, matches.size());
        assertEquals("Arrival", matches.get(0).path("name").asText());

        String newMovie = """
                {"name":"Dune","year":2021,"genres":["Adventure","Sci-fi"],"rating":5}
                """;
        HttpRequest request = HttpRequest.newBuilder(uri("/api/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(newMovie))
                .build();
        HttpResponse<String> added = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, added.statusCode());
        assertEquals("Dune", JSON.readTree(added.body()).path("name").asText());
        assertTrue(movieStore.movies.toString().contains("Dune"));
    }

    @Test
    void rejectsMoviesWithoutAName() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"year\":2024}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(JSON.readTree(response.body()).has("error"));
    }

    @Test
    void exposesLivenessAndDatabaseReadiness() throws Exception {
        assertEquals(200, get("/health/live").statusCode());
        assertEquals(200, get("/health/ready").statusCode());

        movieStore.ready = false;
        assertEquals(503, get("/health/ready").statusCode());
    }

    @Test
    void servesTheMovieLibraryFromTheSameOrigin() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode(), page.body());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.body().contains("Screenroom"));

        HttpResponse<String> stylesheet = get("/assets/app.css");
        assertEquals(200, stylesheet.statusCode());
        assertTrue(stylesheet.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));

        HttpResponse<String> script = get("/assets/app.js");
        assertEquals(200, script.statusCode());
        assertTrue(script.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));

        assertEquals(404, get("/private-file").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static final class TestMovieStore implements MovieStore {
        private final ArrayNode movies;
        private boolean ready = true;

        private TestMovieStore(ArrayNode movies) {
            this.movies = movies;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public ArrayNode find(String searchTerm) {
            if (searchTerm == null || searchTerm.isBlank()) {
                return movies.deepCopy();
            }

            ArrayNode matches = JSON.createArrayNode();
            String normalizedTerm = searchTerm.toLowerCase(java.util.Locale.ROOT);
            for (JsonNode movie : movies) {
                if (movie.toString().toLowerCase(java.util.Locale.ROOT).contains(normalizedTerm)) {
                    matches.add(movie);
                }
            }
            return matches;
        }

        @Override
        public JsonNode add(JsonNode movie) {
            movies.add(movie.deepCopy());
            return movie.deepCopy();
        }
    }
}