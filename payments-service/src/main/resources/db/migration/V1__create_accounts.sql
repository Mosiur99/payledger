CREATE TABLE accounts (
    id            uuid        PRIMARY KEY,
    owner_id      varchar(64) NOT NULL,
    currency      varchar(3)  NOT NULL CHECK (currency IN ('BDT', 'EUR', 'USD')),
    type          varchar(16) NOT NULL CHECK (type IN ('USER', 'SYSTEM')),
    status        varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN')),
    balance_minor bigint      NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL,
    -- System accounts fund deposits, so only they may go negative.
    CONSTRAINT ck_accounts_user_balance_non_negative CHECK (type = 'SYSTEM' OR balance_minor >= 0)
);

CREATE INDEX ix_accounts_owner ON accounts (owner_id, created_at);
