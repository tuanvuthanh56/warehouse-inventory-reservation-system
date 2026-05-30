package com.example.reservation.infrastructure.persistence;

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
@Table(name = "reservation_items")
public class ReservationItemEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private ReservationEntity reservation;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReservationItemEntity() {
    }

    public ReservationItemEntity(UUID id, String sku, int quantity, Instant createdAt) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    void setReservation(ReservationEntity reservation) {
        this.reservation = reservation;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }
}
