package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when held stock has been confirmed and removed from on-hand inventory.
 */
public record InventoryConfirmedEvent(
        UUID messageId,
        UUID reservationId,
        String orderId,
        Instant occurredAt
) {
}
