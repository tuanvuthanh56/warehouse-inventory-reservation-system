--liquibase formatted sql

--changeset warehouse:reservation-002-add-item-rejection-details
ALTER TABLE reservation_items
    ADD COLUMN available_stock INT NULL,
    ADD COLUMN failure_reason TEXT NULL,
    ADD CONSTRAINT chk_reservation_item_available_stock_non_negative CHECK (available_stock IS NULL OR available_stock >= 0);
