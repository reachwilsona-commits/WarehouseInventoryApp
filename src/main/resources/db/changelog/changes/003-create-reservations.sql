--liquibase formatted sql

--changeset warehouse:003-create-reservations
--comment: Reservation aggregate root. order_id is UNIQUE — this is the atomic guard for
--         idempotent POST /reservations behaviour: a duplicate insert raises a unique-constraint
--         violation that the service translates into "return existing reservation".
CREATE TABLE reservations (
    id          UUID          PRIMARY KEY,
    order_id    VARCHAR(128)  NOT NULL UNIQUE,
    status      VARCHAR(16)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT reservations_status_chk
        CHECK (status IN ('PENDING','CONFIRMED','CANCELLED'))
);
--rollback DROP TABLE reservations;
