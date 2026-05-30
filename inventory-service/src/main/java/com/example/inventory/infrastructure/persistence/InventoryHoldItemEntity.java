package com.example.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_hold_items")
public class InventoryHoldItemEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id", nullable = false)
    private InventoryHoldEntity hold;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryHoldItemEntity() {
    }

    public InventoryHoldItemEntity(UUID id, String sku, int quantity, Instant createdAt) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    void setHold(InventoryHoldEntity hold) {
        this.hold = hold;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }
}
