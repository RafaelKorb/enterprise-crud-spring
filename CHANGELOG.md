# Changelog

Todas as mudanças relevantes deste projeto são documentadas aqui.
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Added
- Observabilidade com Micrometer e OpenTelemetry (OTLP): métricas RED de `http.server.requests` com histograma de latência, tracing por requisição cobrindo HTTP, SQL (sem valores de parâmetros) e Redis, e logs JSON (ECS) com `traceId`/`spanId` na imagem Docker.
- Header `X-Trace-Id` em toda resposta de `/api/*`.
- Métrica `idempotency.requests` por desfecho (`executed`, `replayed`, `released`, `mismatch`, `in_progress`, `missing_key`, `unavailable`).
- `docker-compose.observability.yml`: backend local opcional (`grafana/otel-lgtm`) com Grafana, Tempo e Prometheus.

## [0.1.0] - 2026-09-23

### Added
- Camada `application`: casos de uso Open, Credit, Debit, ChangeStatus, Get e List (paginação por cursor), com commands/queries/results em records.
- Exceções de domínio `AccountNotFoundException` e `DocumentNumberAlreadyRegisteredException`.
- Diagramas de fluxo da aplicação em `docs/architecture-flow.md`.
- Convenções de commit, branch e versionamento em `CONTRIBUTING.md`.
- Persistência PostgreSQL: migration `V1__create_account` (unique no documento, check de saldo e status), entidade JPA com `@Version` e adapter do `AccountRepository` com paginação por keyset.
- Configuração dos beans de casos de uso, virtual threads, Hikari `connectionTimeout=2000ms`, `ddl-auto=validate` e `open-in-view=false`.
- `scripts/gradle.sh`: executa o Gradle no JDK local se for 25+, ou no container `eclipse-temurin:25-jdk` (com suporte a Testcontainers).
- CI no GitHub Actions: `./gradlew build` (compilação, testes unitários e de integração) em todo PR e push no `master`.
- API REST de contas versionada em `/api/v1` (versionamento nativo do Spring Framework 7): `POST /accounts`, `GET /accounts/{id}`, `GET /accounts` (cursor), `POST /accounts/{id}/credits`, `POST /accounts/{id}/debits` e `PUT /accounts/{id}/status`, com mapeamento MapStruct e erros em Problem Details (RFC 9457).
- Idempotência em todo `POST` da API via header `Idempotency-Key` (obrigatório), com claim atômico e replay de respostas 2xx no Redis; corpo diferente com a mesma chave → 422, requisição em andamento → 409, Redis indisponível → 503.
- `Dockerfile` multi-stage (JDK 25 no build, `eclipse-temurin:25-jre-alpine` no runtime, usuário não-root, jar em camadas) e `docker-compose.yml` com PostgreSQL 16, Redis 7 e a aplicação, todos com healthcheck.
- `scripts/smoke-test.sh`: teste de fumaça via HTTP contra a stack em execução.
- CI: job `Container smoke test` que builda a imagem, sobe o compose e roda o smoke test em todo PR.
- Endpoint `/actuator/health` (único exposto do Actuator), agregando Postgres e Redis, usado pelos healthchecks de container.
- Teste de arquitetura (ArchUnit) que falha o build se `domain`/`application` dependerem de frameworks ou se alguma dependência apontar para fora.

### Changed
- Valores monetários com mais de 2 casas decimais passam a ser rejeitados pelo domínio.
- `Account` não incrementa mais `version`; o incremento do lock otimista passa a ser responsabilidade da persistência.

[Unreleased]: https://github.com/RafaelKorb/enterprise-crud-spring/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/RafaelKorb/enterprise-crud-spring/releases/tag/v0.1.0
