package com.example.common.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveInventoryCommand(
        UUID messageId,
        UUID reservationId,
        String orderId,
        List<ReservationItemMessage> items,
        Instant occurredAt
) {
}
