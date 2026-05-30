package com.example.reservation.infrastructure.persistence;

import com.example.reservation.domain.model.ReservationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationItemEntity> items = new ArrayList<>();

    protected ReservationEntity() {
    }

    public ReservationEntity(UUID id, String orderId, ReservationStatus status, String failureReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void addItem(ReservationItemEntity item) {
        item.setReservation(this);
        items.add(item);
    }

    public void transitionTo(ReservationStatus status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ReservationItemEntity> getItems() {
        return items;
    }
}
