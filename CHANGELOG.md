# Changelog

Todas as mudanças relevantes deste projeto são documentadas aqui.
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Added
- Camada `application`: casos de uso Open, Credit, Debit, ChangeStatus, Get e List (paginação por cursor), com commands/queries/results em records.
- Exceções de domínio `AccountNotFoundException` e `DocumentNumberAlreadyRegisteredException`.
- Diagramas de fluxo da aplicação em `docs/architecture-flow.md`.
- Convenções de commit, branch e versionamento em `CONTRIBUTING.md`.

### Changed
- Valores monetários com mais de 2 casas decimais passam a ser rejeitados pelo domínio.
- `Account` não incrementa mais `version`; o incremento do lock otimista passa a ser responsabilidade da persistência.
