package com.example.reservation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Reservation(
        UUID id,
        String orderId,
        ReservationStatus status,
        String failureReason,
        List<ReservationItem> items,
        Instant createdAt,
        Instant updatedAt
) {
}
