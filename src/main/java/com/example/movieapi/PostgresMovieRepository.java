package com.example.movieapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class PostgresMovieRepository implements MovieStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSERT_MOVIE = "INSERT INTO movies (data) VALUES (?::jsonb)";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    PostgresMovieRepository(String jdbcUrl, String username, String password, Path seedFile) throws IOException {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        initialize(seedFile);
    }

    @Override
    public boolean isReady() throws IOException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")) {
            return result.next();
        } catch (SQLException exception) {
            throw new IOException("PostgreSQL readiness check failed", exception);
        }
    }

    @Override
    public ArrayNode find(String searchTerm) throws IOException {
        boolean search = searchTerm != null && !searchTerm.isBlank();
        String sql = search
                ? "SELECT data FROM movies WHERE POSITION(LOWER(?) IN LOWER(data::text)) > 0 ORDER BY id"
                : "SELECT data FROM movies ORDER BY id";
        ArrayNode movies = JSON.createArrayNode();

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (search) {
                statement.setString(1, searchTerm);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    movies.add(JSON.readTree(results.getString("data")));
                }
            }
            return movies;
        } catch (SQLException exception) {
            throw new IOException("Could not read movies from PostgreSQL", exception);
        }
    }

    @Override
    public JsonNode add(JsonNode movie) throws IOException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(INSERT_MOVIE)) {
            statement.setString(1, movie.toString());
            statement.executeUpdate();
            return movie.deepCopy();
        } catch (SQLException exception) {
            throw new IOException("Could not save movie to PostgreSQL", exception);
        }
    }

    private void initialize(Path seedFile) throws IOException {
        try {
            Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load()
                    .migrate();
        } catch (FlywayException exception) {
            throw new IOException("Could not apply PostgreSQL schema migrations", exception);
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                if (isEmpty(connection)) {
                    seed(connection, seedFile);
                }
                connection.commit();
            } catch (IOException | SQLException exception) {
                connection.rollback();
                if (exception instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("Could not initialize PostgreSQL movie storage", exception);
            }
        } catch (SQLException exception) {
            throw new IOException("Could not connect to PostgreSQL at " + jdbcUrl, exception);
        }
    }

    private boolean isEmpty(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT NOT EXISTS (SELECT 1 FROM movies)")) {
            return result.next() && result.getBoolean(1);
        }
    }

    private void seed(Connection connection, Path seedFile) throws IOException, SQLException {
        JsonNode data = JSON.readTree(seedFile.toFile());
        if (data == null || !data.isArray()) {
            throw new IOException("Movie seed data must be a JSON array: " + seedFile.toAbsolutePath());
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_MOVIE)) {
            for (JsonNode movie : data) {
                if (!movie.isObject()) {
                    throw new IOException("Each movie in seed data must be a JSON object");
                }
                statement.setString(1, movie.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}