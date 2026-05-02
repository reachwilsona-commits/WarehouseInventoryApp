--liquibase formatted sql

--changeset warehouse:007-seed-data
--comment: Minimal demo seed so the API is exercisable immediately after `docker compose up`.
INSERT INTO products (sku, name, description) VALUES
    ('A100', 'Widget A', 'Standard 4-inch widget'),
    ('B200', 'Widget B', 'Heavy-duty industrial widget'),
    ('C300', 'Widget C', 'Premium oversized widget');

INSERT INTO inventory (sku, total_stock, available_stock, reserved_stock) VALUES
    ('A100', 100, 100, 0),
    ('B200',  50,  50, 0),
    ('C300',  10,  10, 0);
--rollback DELETE FROM inventory WHERE sku IN ('A100','B200','C300');
--rollback DELETE FROM products  WHERE sku IN ('A100','B200','C300');
