package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Command sent after a reservation is accepted by the client and stock should be consumed.
 */
public record ConfirmInventoryCommand(
        UUID messageId,
        UUID reservationId,
        String orderId,
        Instant occurredAt
) {
}
