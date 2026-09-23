# Changelog

Todas as mudanças relevantes deste projeto são documentadas aqui.
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

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
- Teste de arquitetura (ArchUnit) que falha o build se `domain`/`application` dependerem de frameworks ou se alguma dependência apontar para fora.

### Changed
- Valores monetários com mais de 2 casas decimais passam a ser rejeitados pelo domínio.
- `Account` não incrementa mais `version`; o incremento do lock otimista passa a ser responsabilidade da persistência.
