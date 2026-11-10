-- A row without response_status means the first request with this key is still running.
CREATE TABLE idempotency_keys (
    owner_id              varchar(64)  NOT NULL,
    idempotency_key       varchar(64)  NOT NULL,
    request_hash          char(64)     NOT NULL,
    response_status       int,
    response_content_type varchar(100),
    response_body         text,
    created_at            timestamptz  NOT NULL,
    completed_at          timestamptz,
    PRIMARY KEY (owner_id, idempotency_key)
);
