package com.example.reservation.infrastructure.messaging;

import com.example.common.api.ErrorCode;
import com.example.common.messaging.InventoryConfirmedEvent;
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservationRejectedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.MessagingNames;
import com.example.reservation.api.error.ApiException;
import com.example.reservation.application.ReservationApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Dispatches inventory events to reservation application handlers based on the message type header.
 */
@Component
public class InventoryEventListener {
    private final ReservationApplicationService reservationApplicationService;
    private final ObjectMapper objectMapper;

    public InventoryEventListener(ReservationApplicationService reservationApplicationService, ObjectMapper objectMapper) {
        this.reservationApplicationService = reservationApplicationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = MessagingNames.INVENTORY_EVENTS_QUEUE)
    public void onMessage(String payload, @Header(MessagingNames.EVENT_TYPE_HEADER) String eventType) {
        try {
            if (InventoryReservedEvent.class.getSimpleName().equals(eventType)) {
                reservationApplicationService.handleInventoryReserved(objectMapper.readValue(payload, InventoryReservedEvent.class));

            } else if (InventoryReservationRejectedEvent.class.getSimpleName().equals(eventType)) {
                reservationApplicationService.handleInventoryRejected(objectMapper.readValue(payload, InventoryReservationRejectedEvent.class));

            } else if (InventoryConfirmedEvent.class.getSimpleName().equals(eventType)) {
                reservationApplicationService.handleInventoryConfirmed(objectMapper.readValue(payload, InventoryConfirmedEvent.class));

            } else if (InventoryReleasedEvent.class.getSimpleName().equals(eventType)) {
                reservationApplicationService.handleInventoryReleased(objectMapper.readValue(payload, InventoryReleasedEvent.class));
            }
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.MESSAGE_PROCESSING_FAILED, "Unable to deserialize inventory event.", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
