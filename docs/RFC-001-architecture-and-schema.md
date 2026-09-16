# RFC-001: Arquitetura e Especificação Técnica - High-Scale Enterprise CRUD

- **Status:** Proposto (Aprovado para Implementação)
- **Autor:** Staff Engineering
- **Stack:** Java 25 LTS, Spring Boot 4.x, PostgreSQL 16, Redis 7, Flyway, Testcontainers

---

## 1. Contexto e Objetivos

Projetar e construir um CRUD para uma entidade transacional crítica (exemplo: `Account` / `Wallet` ou `ProductOrder`), desenhado para:
- **Alta vazão e concorrência:** Suporte a concorrência otimista sem locks pessimistas no banco de dados.
- **Escalabilidade horizontal:** Aplicação *stateless*.
- **Isolamento de Domínio:** Adoção de Clean Architecture / Portas e Adaptadores (Hexagonal).
- **Consistência:** Operações idempotentes em mutações (POST/PUT/PATCH).
- **Observabilidade completa:** Métricas RED, rastreamento distribuído (Trace ID / Span ID) e structured logging.

---

## 2. Padrão Arquitetural: Clean / Hexagonal

O projeto evita o acoplamento tradicional do Spring Boot (onde anotações do JPA vazam para todo o código). A estrutura de pacotes deve obedecer estritamente:

```text
src/main/java/com/enterprise/crud/
├── domain/                      # Camada Mais Interna (Java Puro, Zero Spring)
│   ├── model/                  # Entidades ricas, Value Objects, Enums
│   ├── exception/              # Exceções de domínio de negócio
│   └── repository/             # Portas de Saída: Interfaces dos Repositórios
├── application/                 # Casos de Uso e Orquestração
│   ├── usecase/                # Interfaces e Implementações dos Casos de Uso
│   └── dto/                    # Command/Query objects e Results (Records)
└── infrastructure/              # Adaptadores de Entrada e Saída
    ├── entrypoints/rest/       # Adaptador HTTP: Controllers, Request/Response DTOs
    │   ├── mapper/             # MapStruct para DTO <-> Domain
    │   └── handler/            # Global Exception Handler (RFC 7807 ProblemDetails)
    ├── persistence/            # Adaptador DB: JPA Entities, Spring Data Repositories
    │   ├── entity/             # JPA Entities anotadas com @Table, @Id
    │   ├── repository/         # Implementação da porta do repositório
    │   └── mapper/             # Mapper JPA Entity <-> Domain Entity
    └── configuration/          # Beans do Spring (@Configuration, Beans de UseCases)