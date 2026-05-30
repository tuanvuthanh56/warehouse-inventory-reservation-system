package com.example.common.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReservationRejectedEvent(
        UUID messageId,
        UUID reservationId,
        String orderId,
        String reason,
        List<UnavailableItemMessage> unavailableItems,
        Instant occurredAt
) {
}
