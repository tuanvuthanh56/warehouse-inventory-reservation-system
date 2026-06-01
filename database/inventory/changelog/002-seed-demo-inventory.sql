--liquibase formatted sql

--changeset warehouse:inventory-002-seed-demo-inventory
INSERT INTO products (sku, name, description, created_at, updated_at)
VALUES
    ('A100', 'Demo Product A100', 'Seed product for reservation examples.', NOW(), NOW()),
    ('B200', 'Demo Product B200', 'Seed product for reservation examples.', NOW(), NOW())
ON CONFLICT (sku) DO NOTHING;

INSERT INTO inventory (sku, on_hand_stock, available_stock, reserved_stock, version, updated_at)
VALUES
    ('A100', 100, 100, 0, 0, NOW()),
    ('B200', 50, 50, 0, 0, NOW())
ON CONFLICT (sku) DO NOTHING;
