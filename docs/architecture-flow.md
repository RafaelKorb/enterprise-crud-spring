# Fluxo da Aplicação - Enterprise CRUD (Account)

Complementa o [RFC-001](RFC-001-architecture-and-schema.md). Os diagramas usam Mermaid (renderizado nativamente no GitHub/GitLab e no IntelliJ).

**Legenda de status:** ✅ implementado · 🕐 planejado (pacote ainda vazio). Nomes de endpoints e casos de uso marcados como 🕐 são **propostas**.

---

## 1. Visão geral das camadas (Hexagonal)

As dependências apontam sempre para dentro: `infrastructure → application → domain`. O `domain` não conhece Spring, JPA nem Redis.

```mermaid
flowchart LR
    client([Cliente HTTP])

    subgraph infra_in["infrastructure / entrypoints.rest 🕐"]
        ctrl[AccountController]
        rmap[REST Mapper<br/>MapStruct]
        handler[GlobalExceptionHandler<br/>RFC 7807 ProblemDetails]
    end

    subgraph app["application 🕐"]
        uc[Use Cases<br/>Open / Credit / Debit /<br/>ChangeStatus / Get / List]
        dto[Commands / Queries / Results<br/>records]
    end

    subgraph domain["domain ✅ (Java puro)"]
        agg[Account<br/>aggregate root]
        vo[AccountId · DocumentNumber<br/>AccountStatus]
        port[[AccountRepository<br/>porta de saída]]
        ex[Domain Exceptions]
    end

    subgraph infra_out["infrastructure / persistence 🕐"]
        adapter[AccountRepositoryAdapter<br/>implements AccountRepository]
        pmap[Persistence Mapper]
        jpa["AccountJpaEntity<br/>@Version"]
        springrepo[Spring Data<br/>JpaRepository]
    end

    subgraph ext["Infra externa"]
        pg[(PostgreSQL 16<br/>Flyway)]
        redis[(Redis 7<br/>idempotência)]
    end

    client -->|JSON| ctrl
    ctrl --> rmap --> dto
    ctrl --> uc
    uc --> agg
    agg --> vo
    uc --> port
    port -. implementado por .-> adapter
    adapter --> pmap --> jpa
    adapter --> springrepo --> pg
    ctrl -. Idempotency-Key .-> redis
    agg -. lança .-> ex
    ex -. capturada por .-> handler
    handler -->|application/problem+json| client
```

---

## 2. Fluxo de mutação: débito com idempotência e lock otimista

Exemplo de ponta a ponta para `POST /accounts/{id}/debits` 🕐. Crédito, bloqueio e encerramento seguem o mesmo esqueleto e só trocam o método chamado no agregado.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant RC as AccountController
    participant R as Redis
    participant UC as DebitAccountUseCase
    participant A as Account (domain)
    participant RA as RepositoryAdapter
    participant DB as PostgreSQL

    C->>RC: POST /accounts/{id}/debits<br/>Idempotency-Key: k1 · {amount}
    RC->>R: SET idem:k1 PROCESSING NX EX ttl
    alt chave já existe com resposta
        R-->>RC: resposta armazenada
        RC-->>C: replay da resposta original
    else chave em PROCESSING
        RC-->>C: 409 Conflict
    else chave nova
        RC->>UC: execute(DebitCommand)
        UC->>RA: findById(AccountId)
        RA->>DB: SELECT ... WHERE id = ?
        DB-->>RA: row (version = n)
        RA-->>UC: Account
        UC->>A: debit(amount)
        Note over A: valida amount > 0<br/>status == ACTIVE<br/>saldo suficiente
        A-->>UC: ok (estado alterado)
        UC->>RA: save(account)
        RA->>DB: UPDATE ... SET version = n+1<br/>WHERE id = ? AND version = n
        alt 1 linha afetada
            DB-->>RA: ok
            RA-->>UC: Account
            UC-->>RC: AccountResult
            RC->>R: SET idem:k1 {status, body}
            RC-->>C: 200 OK
        else 0 linhas (escrita concorrente)
            DB-->>RA: OptimisticLockException
            RC->>R: DEL idem:k1
            RC-->>C: 409 Conflict (ProblemDetails)
        end
    end
```

---

## 3. Fluxo de consulta: listagem com paginação por cursor (keyset)

Sem `OFFSET/LIMIT`. O cursor é o último `id` da página anterior e é usado pelo `AccountRepository.findPage` ✅ (porta já definida).

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant RC as AccountController
    participant UC as ListAccountsUseCase
    participant RA as RepositoryAdapter
    participant DB as PostgreSQL

    C->>RC: GET /accounts?cursor=abc&limit=50
    RC->>UC: execute(ListAccountsQuery)
    UC->>RA: findPage(Optional[cursor], limit + 1)
    RA->>DB: SELECT ... WHERE id > :cursor<br/>ORDER BY id LIMIT :limit+1
    DB-->>RA: até limit+1 linhas
    RA-->>UC: lista de Account
    Note over UC: veio limit+1? então há próxima página<br/>nextCursor = id do último item exibido
    UC-->>RC: PageResult(items, nextCursor)
    RC-->>C: 200 {items, nextCursor}
```

---

## 4. Ciclo de vida da conta (regras em `AccountStatus` / `Account`) ✅

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Account.open(document)
    ACTIVE --> BLOCKED: block()
    BLOCKED --> ACTIVE: activate()
    ACTIVE --> CLOSED: close() [saldo == 0]
    BLOCKED --> CLOSED: close() [saldo == 0]
    CLOSED --> [*]

    note right of ACTIVE
        credit() e debit() só são permitidos em ACTIVE.
        Transição para o mesmo status é no-op.
    end note
```

---

## 5. Mapeamento de erros → HTTP (GlobalExceptionHandler) 🕐

| Origem | Exceção | HTTP proposto |
|---|---|---|
| Domain | `InvalidAmountException` | 422 Unprocessable Content |
| Domain | `InvalidDocumentNumberException` | 422 Unprocessable Content |
| Domain | `InsufficientBalanceException` | 422 Unprocessable Content |
| Domain | `InvalidAccountStatusTransitionException` | 409 Conflict |
| Application | conta não encontrada | 404 Not Found |
| Application | documento já cadastrado | 409 Conflict |
| Infra | `OptimisticLockingFailureException` | 409 Conflict |
| Infra | Bean Validation (`@Valid`) | 400 Bad Request |

---

## 6. Ponto em aberto

**Versão controlada pelo domínio vs. JPA `@Version`.** Hoje `Account.touch()` incrementa `version` em memória. Se o adaptador copiar esse valor já incrementado para a entidade JPA, o Hibernate compara `n+1` com o `n` gravado no banco e lança `OptimisticLockException` em **toda** atualização. Há duas saídas:

1. O mapper grava na entidade a versão **lida** do banco e deixa o Hibernate incrementar.
2. O domínio deixa de incrementar e só carrega a versão.

Decidir antes de implementar `infrastructure/persistence`.
