# Engineering Guidelines - Spring Boot 4.x & Java 25 Enterprise CRUD

## Architecture Pattern: Clean / Hexagonal (Ports & Adapters)
- Adhere strictly to `docs/RFC-001-architecture-and-schema.md`.
- Strict Layer Isolation:
  - `domain`: Pure Java 25. Absolutely NO Spring, Jakarta, or Hibernate imports. Entities encapsulate invariants and business rules.
  - `application`: Use cases, Ports (interfaces), and Command/Query records.
  - `infrastructure`: Adapters (REST Controllers, JPA/Postgres repositories, Redis, Spring configuration beans).

## Java 25 & Spring Boot 4.x Idioms
- Baseline: Java 25 LTS, Spring Boot 4.x (built on Spring Framework 7 and Jakarta EE 11).
- Enable preview features only if explicitly needed; prefer standard Virtual Threads (`spring.threads.virtual.enabled=true`).
- Use Java 25 features: Records for DTOs and immutable Value Objects, pattern matching for switch/instanceof, and text blocks for raw migrations.
- Mappers: Use MapStruct with `lombok-mapstruct-binding` for zero-reflection mappings between Domain, JPA, and HTTP layers.

## High Scalability & Concurrency
- Optimistic locking using `@Version` (BIGINT) on mutable aggregates.
- Keyset/cursor pagination for all query endpoints (no `OFFSET / LIMIT`).
- Connection pool tuned via HikariCP (connectionTimeout=2000ms).

## Build & Test Commands (Gradle Wrapper)
- Always invoke Gradle through `scripts/gradle.sh`: it uses the host JVM when it is JDK 25+, otherwise runs `./gradlew` inside `eclipse-temurin:25-jdk` (Docker socket shared for Testcontainers).
- Build & Compile: `scripts/gradle.sh clean compileJava`
- Run all tests: `scripts/gradle.sh test`
- Run single test: `scripts/gradle.sh test --tests "com.enterprise.crud.*AccountUseCaseTest"`
- Continuous build check: `scripts/gradle.sh check -x test`
- CI (`.github/workflows/ci.yml`) runs `./gradlew build` on every PR and push to `master`.

## Agent Workflow & Quality Gate
- Before concluding any task:
  1. Ensure code compiles with `scripts/gradle.sh compileJava`.
  2. Verify unit and integration tests pass with `scripts/gradle.sh test`.
  3. No architectural leakage across layer boundaries.

## Containerization Guidelines
- Multi-stage Dockerfile: build stage with Gradle/JDK 25, runtime stage with eclipse-temurin:25-jre-alpine or distroless.
- Do NOT run containers as root (use dedicated non-privileged user).
- Local orchestration via `docker-compose.yml` containing PostgreSQL 16 and Redis 7 with healthchecks.