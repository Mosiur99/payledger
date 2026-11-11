-- Written in the same transaction as the ledger change, so an event exists if and only if the change committed.
-- The relay publishes unpublished rows to Kafka and stamps published_at.
CREATE TABLE outbox_events (
    id           uuid         PRIMARY KEY,
    topic        varchar(100) NOT NULL,
    message_key  varchar(100) NOT NULL,
    event_type   varchar(64)  NOT NULL,
    payload      jsonb        NOT NULL,
    created_at   timestamptz  NOT NULL,
    published_at timestamptz
);

CREATE INDEX ix_outbox_events_unpublished ON outbox_events (id) WHERE published_at IS NULL;
