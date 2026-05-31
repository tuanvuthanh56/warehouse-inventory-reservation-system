package com.example.common.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event published when at least one requested SKU cannot be reserved.
 */
public record InventoryReservationRejectedEvent(
        UUID messageId,
        UUID reservationId,
        String orderId,
        String reason,
        List<UnavailableItemMessage> unavailableItems,
        Instant occurredAt
) {
}
