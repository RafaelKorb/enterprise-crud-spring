CREATE TABLE account (
    id              UUID            PRIMARY KEY,
    document_number VARCHAR(14)     NOT NULL,
    balance         NUMERIC(19, 2)  NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL,
    CONSTRAINT uk_account_document_number UNIQUE (document_number),
    CONSTRAINT ck_account_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'))
);
