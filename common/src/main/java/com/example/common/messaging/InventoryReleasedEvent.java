package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when held stock has been released back to available inventory.
 */
public record InventoryReleasedEvent(
        UUID messageId,
        UUID reservationId,
        String orderId,
        Instant occurredAt
) {
}
