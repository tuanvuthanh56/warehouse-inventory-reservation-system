package com.example.reservation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * Concrete reservation type for the normal reservation flow.
 */
public record StandardReservation(
        UUID id,
        String orderId,
        ReservationStatus status,
        String failureReason,
        List<ReservationItem> items,
        Instant createdAt,
        Instant updatedAt
) implements Reservation {
}
