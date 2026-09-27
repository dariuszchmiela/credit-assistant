# FR-010: multi-stage build of the Spring Boot application. Configuration (database, LLM endpoint, credentials)
# comes only from environment variables at runtime; nothing environment-specific is baked into the image.

FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# Tests are not run here: the integration suite needs a Docker daemon (Testcontainers). Run them with mvn test.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package \
    && cp target/credit-assistant-*.jar application.jar

FROM eclipse-temurin:25.0.4.1_1-jre
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/application.jar application.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
