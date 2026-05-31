ALTER TABLE inventory_hold_items
    DROP CONSTRAINT inventory_hold_items_sku_fkey;

ALTER TABLE inventory
    DROP CONSTRAINT inventory_sku_fkey;

ALTER TABLE inventory_hold_items
    ADD CONSTRAINT fk_inventory_hold_items_inventory_sku
        FOREIGN KEY (sku) REFERENCES inventory(sku);

DROP TABLE products;
