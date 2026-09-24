# Contribuindo

## Fluxo de branches

- `master` é sempre compilável e com testes verdes; nada é commitado direto nele.
- Todo trabalho acontece em uma branch curta, integrada via Pull Request:

| Prefixo | Uso | Exemplo |
|---|---|---|
| `feat/` | nova funcionalidade | `feat/account-persistence` |
| `fix/` | correção de bug | `fix/keyset-cursor-order` |
| `refactor/` | mudança interna sem alterar comportamento | `refactor/account-mapper` |
| `docs/`, `test/`, `build/`, `ci/`, `chore/` | demais tipos (mesmos do commit) | `build/jdk25-docker` |

## Mensagens de commit — [Conventional Commits 1.0](https://www.conventionalcommits.org/pt-br/v1.0.0/)

```
<tipo>(<escopo>): <resumo no imperativo, minúsculo, sem ponto final, até 72 chars>

<corpo: o PORQUÊ da mudança, quebrado em 72 colunas>

<rodapé: BREAKING CHANGE: ..., Refs: #123>
```

- **Tipos:** `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `revert`.
- **Escopos:** a camada ou área afetada — `domain`, `application`, `persistence`, `rest`, `config`, `architecture`, `deps`.
- **Idioma:** inglês, igual ao código.
- **Atomicidade:** um commit = uma mudança coerente que compila e passa nos testes. Testes vão no mesmo commit do código que cobrem.
- **Breaking change:** `!` após o tipo/escopo (`feat(rest)!: ...`) e rodapé `BREAKING CHANGE:` descrevendo a migração.

## Versionamento — [SemVer 2.0](https://semver.org/lang/pt-BR/)

- A versão fica em `build.gradle` (`version = 'X.Y.Z-SNAPSHOT'`).
- Enquanto a API não for estável, o projeto fica em `0.y.z`: `feat` incrementa **y**, `fix` incrementa **z**.
- A partir de `1.0.0`: breaking change → major, `feat` → minor, `fix`/`perf` → patch.
- **Release:**
  1. Remover `-SNAPSHOT` da versão e mover a seção `[Unreleased]` do `CHANGELOG.md` para `[X.Y.Z] - AAAA-MM-DD`.
  2. Commit `chore(release): vX.Y.Z` e tag anotada `git tag -a vX.Y.Z -m "vX.Y.Z"`.
  3. Abrir o próximo ciclo com `X.Y+1.0-SNAPSHOT` em `chore(release): start X.Y+1.0 development`.

## Changelog

O `CHANGELOG.md` segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). Toda PR com impacto visível adiciona uma linha em `[Unreleased]` (Added / Changed / Fixed / Removed).

## Execução local

```bash
docker compose up -d --build --wait   # Postgres 16 + Redis 7 + app em http://localhost:8080
scripts/smoke-test.sh                 # fluxo real via HTTP: abertura, crédito, débito idempotente, erros
docker compose down -v                # para tudo e apaga os volumes
```

Para rodar a aplicação pela IDE, suba só as dependências (`docker compose up -d postgres redis`) e defina `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/crud`, `SPRING_DATASOURCE_USERNAME=crud` e `SPRING_DATASOURCE_PASSWORD=crud`. Alternativa sem compose: executar `TestCrudApplication` (em `src/test`), que sobe Postgres e Redis via Testcontainers.

## Quality gate antes do PR

```bash
scripts/gradle.sh clean compileJava
scripts/gradle.sh test
```

`scripts/gradle.sh` usa o JDK local quando ele é 25+; caso contrário roda o `./gradlew` dentro do container `eclipse-temurin:25-jdk` (requer Docker). A CI (`.github/workflows/ci.yml`) roda `./gradlew build` em todo PR e push no `master`, e o PR só deve ser mergeado com ela verde.

Sem vazamento entre camadas: `domain` e `application` não importam Spring, Jakarta, Hibernate ou Lombok. O `LayerArchitectureTest` (ArchUnit) faz essa verificação em todo build.
