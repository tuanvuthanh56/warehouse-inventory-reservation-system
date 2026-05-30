package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

public record ReleaseInventoryCommand(
        UUID messageId,
        UUID reservationId,
        String orderId,
        String reason,
        Instant occurredAt
) {
}
