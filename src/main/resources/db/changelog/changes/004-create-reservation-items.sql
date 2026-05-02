--liquibase formatted sql

--changeset warehouse:004-create-reservation-items
--comment: Line items for a reservation. ON DELETE CASCADE keeps lifetime tied to parent.
CREATE TABLE reservation_items (
    id              BIGSERIAL    PRIMARY KEY,
    reservation_id  UUID         NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku             VARCHAR(64)  NOT NULL REFERENCES products(sku)    ON DELETE RESTRICT,
    quantity        INTEGER      NOT NULL,
    CONSTRAINT reservation_items_qty_positive CHECK (quantity > 0),
    CONSTRAINT reservation_items_unique_sku   UNIQUE (reservation_id, sku)
);
--rollback DROP TABLE reservation_items;
