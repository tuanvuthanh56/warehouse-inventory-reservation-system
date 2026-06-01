--liquibase formatted sql

--changeset warehouse:inventory-001-init-schema
CREATE TABLE products (
    sku VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory (
    sku VARCHAR(100) PRIMARY KEY REFERENCES products(sku),
    on_hand_stock INT NOT NULL,
    available_stock INT NOT NULL,
    reserved_stock INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_inventory_on_hand_non_negative CHECK (on_hand_stock >= 0),
    CONSTRAINT chk_inventory_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT chk_inventory_reserved_non_negative CHECK (reserved_stock >= 0),
    CONSTRAINT chk_inventory_available_reserved_valid CHECK (available_stock + reserved_stock <= on_hand_stock)
);

CREATE TABLE inventory_holds (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE,
    order_id VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_holds_status ON inventory_holds(status);

CREATE TABLE inventory_hold_items (
    id UUID PRIMARY KEY,
    hold_id UUID NOT NULL REFERENCES inventory_holds(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL REFERENCES products(sku),
    quantity INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_inventory_hold_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_inventory_hold_item_sku UNIQUE (hold_id, sku)
);

CREATE INDEX idx_inventory_hold_items_hold_id ON inventory_hold_items(hold_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_inventory_outbox_status_created ON outbox_events(status, created_at);

CREATE TABLE inbox_messages (
    message_id UUID PRIMARY KEY,
    message_type VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
