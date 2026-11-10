CREATE TABLE transactions (
    id           uuid         PRIMARY KEY,
    type         varchar(16)  NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    status       varchar(16)  NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    reference    varchar(140),
    initiated_by varchar(64)  NOT NULL,
    created_at   timestamptz  NOT NULL
);

-- Append-only: rows are never updated or deleted; corrections are new reversing entries.
CREATE TABLE ledger_entries (
    id             uuid        PRIMARY KEY,
    transaction_id uuid        NOT NULL REFERENCES transactions (id),
    account_id     uuid        NOT NULL REFERENCES accounts (id),
    direction      varchar(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor   bigint      NOT NULL CHECK (amount_minor > 0),
    currency       varchar(3)  NOT NULL,
    created_at     timestamptz NOT NULL
);

CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id, id DESC);
CREATE INDEX ix_ledger_entries_transaction ON ledger_entries (transaction_id);
