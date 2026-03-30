CREATE TABLE event_store (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    aggregate_id    UUID         NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    sequence_number BIGINT       NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_event_store PRIMARY KEY (id),
    CONSTRAINT uq_event_store_version UNIQUE (aggregate_id, sequence_number)
);

CREATE INDEX idx_event_store_aggregate ON event_store (aggregate_id, sequence_number ASC);
CREATE INDEX idx_event_store_type ON event_store (aggregate_type, occurred_at DESC);

CREATE TABLE snapshots (
    aggregate_id    UUID        NOT NULL,
    aggregate_type  VARCHAR(50) NOT NULL,
    version         BIGINT      NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_snapshots PRIMARY KEY (aggregate_id, aggregate_type)
);
