package com.example.inventory.infrastructure.persistence;

import com.example.inventory.domain.model.InventoryHoldStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_holds")
public class InventoryHoldEntity {
    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryHoldStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "hold", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryHoldItemEntity> items = new ArrayList<>();

    protected InventoryHoldEntity() {
    }

    public InventoryHoldEntity(UUID id, UUID reservationId, String orderId, InventoryHoldStatus status, Instant createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void addItem(InventoryHoldItemEntity item) {
        item.setHold(this);
        items.add(item);
    }

    // HELD -> CONFIRMED.
    public void markConfirmed() {
        this.status = InventoryHoldStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    // HELD -> RELEASED.
    public void markReleased() {
        this.status = InventoryHoldStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getOrderId() {
        return orderId;
    }

    public InventoryHoldStatus getStatus() {
        return status;
    }

    public List<InventoryHoldItemEntity> getItems() {
        return items;
    }
}
