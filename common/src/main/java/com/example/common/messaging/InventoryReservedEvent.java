package com.example.common.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID messageId,
        UUID reservationId,
        String orderId,
        UUID holdId,
        List<ReservationItemMessage> items,
        Instant occurredAt
) {
}
