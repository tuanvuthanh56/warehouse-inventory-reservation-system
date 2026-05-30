CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    failure_reason TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_reservation_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_reservation_item_sku UNIQUE (reservation_id, sku)
);

CREATE INDEX idx_reservation_items_reservation_id ON reservation_items(reservation_id);

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

CREATE INDEX idx_reservation_outbox_status_created ON outbox_events(status, created_at);

CREATE TABLE inbox_messages (
    message_id UUID PRIMARY KEY,
    message_type VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
