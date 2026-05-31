package com.example.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "inventory")
public class InventoryEntity {

    @Id
    private String sku;

    @Column(name = "on_hand_stock", nullable = false)
    private int onHandStock;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryEntity() {
    }

    public InventoryEntity(String sku, int onHandStock, int availableStock, int reservedStock, Instant updatedAt) {
        this.sku = sku;
        this.onHandStock = onHandStock;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.updatedAt = updatedAt;
    }

    /**
     * Moves quantity from available stock into reserved stock.
     */
    public void reserve(int quantity) {
        availableStock -= quantity;
        reservedStock += quantity;
        updatedAt = Instant.now();
    }

    /**
     * Consumes quantity that was already reserved.
     */
    public void confirm(int quantity) {
        reservedStock -= quantity;
        onHandStock -= quantity;
        updatedAt = Instant.now();
    }

    /**
     * Returns reserved quantity back to available stock.
     */
    public void release(int quantity) {
        availableStock += quantity;
        reservedStock -= quantity;
        updatedAt = Instant.now();
    }

    public String getSku() {
        return sku;
    }

    public int getOnHandStock() {
        return onHandStock;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
