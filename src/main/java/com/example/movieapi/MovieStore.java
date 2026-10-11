package com.example.movieapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

interface MovieStore {
    boolean isReady() throws IOException;

    ArrayNode find(String searchTerm) throws IOException;

    JsonNode add(JsonNode movie) throws IOException;
}