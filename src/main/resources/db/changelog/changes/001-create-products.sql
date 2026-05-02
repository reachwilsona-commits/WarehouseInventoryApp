--liquibase formatted sql

--changeset warehouse:001-create-products
--comment: Products catalog. SKU is the natural key referenced by inventory and reservation_items.
CREATE TABLE products (
    sku         VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE products;
