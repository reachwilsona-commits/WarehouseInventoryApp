--liquibase formatted sql

--changeset warehouse:002-create-inventory
--comment: Inventory row per SKU. CHECK constraints enforce non-negative invariants at the DB layer.
--         "version" supports JPA optimistic locking; we additionally rely on SELECT ... FOR UPDATE
--         (pessimistic locking) for the hot reservation path to serialize contended rows.
CREATE TABLE inventory (
    sku             VARCHAR(64)  PRIMARY KEY REFERENCES products(sku) ON DELETE RESTRICT,
    total_stock     INTEGER      NOT NULL,
    available_stock INTEGER      NOT NULL,
    reserved_stock  INTEGER      NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inventory_total_nonneg     CHECK (total_stock     >= 0),
    CONSTRAINT inventory_available_nonneg CHECK (available_stock >= 0),
    CONSTRAINT inventory_reserved_nonneg  CHECK (reserved_stock  >= 0),
    CONSTRAINT inventory_balance          CHECK (available_stock + reserved_stock = total_stock)
);
--rollback DROP TABLE inventory;
