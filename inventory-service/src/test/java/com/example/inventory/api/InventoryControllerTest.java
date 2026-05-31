package com.example.inventory.api;

import com.example.inventory.api.dto.InventoryResponse;
import com.example.inventory.application.InventoryApplicationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryControllerTest {
    private final InventoryApplicationService service = mock(InventoryApplicationService.class);
    private final InventoryController controller = new InventoryController(service);

    @Test
    void getStockDelegatesToApplicationService() {
        var response = new InventoryResponse("A100", 100, 90, 10, Instant.now());
        when(service.getStock("A100")).thenReturn(response);

        assertThat(controller.getStock("A100")).isEqualTo(response);
    }

    @Test
    void listStockDelegatesToApplicationService() {
        var responses = List.of(
                new InventoryResponse("A100", 100, 90, 10, Instant.now()),
                new InventoryResponse("B200", 50, 50, 0, Instant.now())
        );
        when(service.listStock()).thenReturn(responses);

        assertThat(controller.listStock()).isEqualTo(responses);
    }
}
