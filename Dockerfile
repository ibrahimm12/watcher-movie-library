FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
COPY web ./web
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
COPY movies-compact.json ./movies-compact.json
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system movieapi \
    && useradd --system --gid movieapi --create-home movieapi

WORKDIR /app

COPY --from=build --chown=movieapi:movieapi /workspace/target/movie-api-1.0.0.jar app.jar
COPY --from=build --chown=movieapi:movieapi /workspace/movies-compact.json movies-compact.json

USER movieapi
EXPOSE 8080
ENV PORT=8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/health/ready || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dmovies.file=/app/movies-compact.json", "-jar", "/app/app.jar"]