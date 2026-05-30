package com.example.reservation.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String orderId,
        String status,
        String failureReason,
        List<ReservationItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
