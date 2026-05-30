package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

public record ConfirmInventoryCommand(
        UUID messageId,
        UUID reservationId,
        String orderId,
        Instant occurredAt
) {
}
