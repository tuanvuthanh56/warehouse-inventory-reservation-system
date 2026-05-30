package com.example.inventory.infrastructure.messaging;

import com.example.common.messaging.ConfirmInventoryCommand;
import com.example.common.messaging.ReleaseInventoryCommand;
import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.ReserveInventoryCommand;
import com.example.inventory.api.error.ApiException;
import com.example.inventory.application.InventoryApplicationService;
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

class InventoryCommandListenerTest {
    private final InventoryApplicationService service = mock(InventoryApplicationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InventoryCommandListener listener = new InventoryCommandListener(service, objectMapper);

    @Test
    void dispatchesReserveCommand() throws Exception {
        var command = new ReserveInventoryCommand(UUID.randomUUID(), UUID.randomUUID(), "ORD-1",
                List.of(new ReservationItemMessage("A100", 1)), Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(command), ReserveInventoryCommand.class.getSimpleName());

        verify(service).reserve(command);
    }

    @Test
    void dispatchesConfirmCommand() throws Exception {
        var command = new ConfirmInventoryCommand(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(command), ConfirmInventoryCommand.class.getSimpleName());

        verify(service).confirm(command);
    }

    @Test
    void dispatchesReleaseCommand() throws Exception {
        var command = new ReleaseInventoryCommand(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", "CLIENT_CANCELLED", Instant.now());

        listener.onMessage(objectMapper.writeValueAsString(command), ReleaseInventoryCommand.class.getSimpleName());

        verify(service).release(command);
    }

    @Test
    void ignoresUnknownEventType() {
        listener.onMessage("{}", "UnknownCommand");

        verify(service, never()).reserve(org.mockito.ArgumentMatchers.any());
        verify(service, never()).confirm(org.mockito.ArgumentMatchers.any());
        verify(service, never()).release(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrapsMalformedJsonAsApiException() {
        assertThatThrownBy(() -> listener.onMessage("{bad json", ReserveInventoryCommand.class.getSimpleName()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Unable to deserialize inventory command.");
    }
}
