-- One row per event this service has handled. Kafka delivers at least once, so the primary key
-- turns a redelivered event into a no-op.
CREATE TABLE processed_events (
    event_id     uuid        PRIMARY KEY,
    event_type   varchar(64) NOT NULL,
    processed_at timestamptz NOT NULL
);

CREATE TABLE notifications (
    id             uuid         PRIMARY KEY,
    event_id       uuid         NOT NULL REFERENCES processed_events (event_id),
    recipient_id   varchar(64)  NOT NULL,
    kind           varchar(24)  NOT NULL CHECK (kind IN ('MONEY_SENT', 'MONEY_RECEIVED', 'DEPOSIT_RECEIVED')),
    transaction_id uuid         NOT NULL,
    message        varchar(280) NOT NULL,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT ux_notifications_event_recipient_kind UNIQUE (event_id, recipient_id, kind)
);

CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, created_at DESC);
