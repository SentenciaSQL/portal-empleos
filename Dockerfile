FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:resolve dependency:resolve-plugins
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21.0.8_9-jre-jammy AS runtime
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --create-home app \
    && mkdir -p /app/data/uploads \
    && chown -R 10001:10001 /app
WORKDIR /app
COPY --link --from=build --chown=10001:10001 /src/target/empleord-api.jar /app/empleord-api.jar
USER 10001
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java", "-jar", "/app/empleord-api.jar"]
