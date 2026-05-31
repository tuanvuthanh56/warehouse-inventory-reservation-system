package com.example.common.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Command sent by Reservation Service to request an atomic inventory hold.
 */
public record ReserveInventoryCommand(

        UUID messageId,

        UUID reservationId,

        String orderId,

        List<ReservationItemMessage> items,

        Instant occurredAt
) {
}
