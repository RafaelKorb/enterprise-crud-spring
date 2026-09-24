# syntax=docker/dockerfile:1

# ---- Build: compile with JDK 25 and split the boot jar into layers -------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first, so dependency resolution is cached until they change
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test \
    && jar=$(find build/libs -name '*.jar' ! -name '*-plain.jar') \
    && java -Djarmode=tools -jar "$jar" extract --layers --launcher --destination extracted

# ---- Runtime: JRE only, non-root ------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app

# Least to most frequently changing, so rebuilds reuse the dependency layers.
# Files stay owned by root: the app user can read but not modify them.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

# Size the heap from the container memory limit and let the orchestrator restart on OOM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
