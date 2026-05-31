package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Command sent when held stock should be returned to available inventory.
 */
public record ReleaseInventoryCommand(
        UUID messageId,
        UUID reservationId,
        String orderId,

        String reason,
        Instant occurredAt
) {
}
