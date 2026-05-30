package com.example.reservation.infrastructure.persistence;

import com.example.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationEntityTest {
    @Test
    void addItemSetsRelationshipAndTransitionUpdatesStateAndReason() {
        Instant createdAt = Instant.now();
        var reservation = new ReservationEntity(
                UUID.randomUUID(),
                "ORD-1",
                ReservationStatus.RESERVING,
                null,
                createdAt,
                createdAt
        );
        var item = new ReservationItemEntity(UUID.randomUUID(), "A100", 2, createdAt);

        reservation.addItem(item);
        reservation.transitionTo(ReservationStatus.REJECTED, "INSUFFICIENT_STOCK");

        assertThat(reservation.getItems()).containsExactly(item);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getFailureReason()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(reservation.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(item.getSku()).isEqualTo("A100");
        assertThat(item.getQuantity()).isEqualTo(2);
    }
}
