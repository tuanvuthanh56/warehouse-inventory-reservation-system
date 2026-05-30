package com.example.reservation.infrastructure.messaging;

import com.example.common.messaging.InventoryConfirmedEvent;
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservationRejectedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.UnavailableItemMessage;
import com.example.reservation.api.error.ApiException;
import com.example.reservation.application.ReservationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InventoryEventListenerTest {
    private final ReservationApplicationService service = mock(ReservationApplicationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InventoryEventListener listener = new InventoryEventListener(service, objectMapper);

    @Test
    void dispatchesInventoryReservedEvent() throws Exception {
        var event = new InventoryReservedEvent(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", UUID.randomUUID(),
                List.of(new ReservationItemMessage("A100", 1)), Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(event), InventoryReservedEvent.class.getSimpleName());

        verify(service).handleInventoryReserved(event);
    }

    @Test
    void dispatchesInventoryRejectedEvent() throws Exception {
        var event = new InventoryReservationRejectedEvent(UUID.randomUUID(), UUID.randomUUID(), "ORD-1",
                "INSUFFICIENT_STOCK", List.of(new UnavailableItemMessage("A100", 3, 1)), Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(event), InventoryReservationRejectedEvent.class.getSimpleName());

        verify(service).handleInventoryRejected(event);
    }

    @Test
    void dispatchesInventoryConfirmedEvent() throws Exception {
        var event = new InventoryConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(event), InventoryConfirmedEvent.class.getSimpleName());

        verify(service).handleInventoryConfirmed(event);
    }

    @Test
    void dispatchesInventoryReleasedEvent() throws Exception {
        var event = new InventoryReleasedEvent(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(event), InventoryReleasedEvent.class.getSimpleName());

        verify(service).handleInventoryReleased(event);
    }

    @Test
    void ignoresUnknownEventType() {
        listener.onMessage("{}", "UnknownEvent");

        verify(service, never()).handleInventoryReserved(org.mockito.ArgumentMatchers.any());
        verify(service, never()).handleInventoryRejected(org.mockito.ArgumentMatchers.any());
        verify(service, never()).handleInventoryConfirmed(org.mockito.ArgumentMatchers.any());
        verify(service, never()).handleInventoryReleased(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrapsMalformedJsonAsApiException() {
        assertThatThrownBy(() -> listener.onMessage("{bad json", InventoryReservedEvent.class.getSimpleName()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Unable to deserialize inventory event.");
    }
}
