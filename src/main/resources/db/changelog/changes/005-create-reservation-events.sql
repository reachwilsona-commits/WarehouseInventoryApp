--liquibase formatted sql

--changeset warehouse:005-create-reservation-events
--comment: Outbox-style durable event log. Written in the same transaction as the state change,
--         so events cannot be lost if the process dies before publishing. The "published_at"
--         column lets future infrastructure (NATS, Kafka) drain unpublished rows safely.
CREATE TABLE reservation_events (
    id              BIGSERIAL    PRIMARY KEY,
    reservation_id  UUID         NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
--rollback DROP TABLE reservation_events;
