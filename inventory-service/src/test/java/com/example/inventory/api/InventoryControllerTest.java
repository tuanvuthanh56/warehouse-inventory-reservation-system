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

        var entity = controller.getStock("A100");

        assertThat(entity.data()).isEqualTo(response);
        assertThat(entity.error()).isNull();
    }

    @Test
    void listStockDelegatesToApplicationService() {
        var responses = List.of(
                new InventoryResponse("A100", 100, 90, 10, Instant.now()),
                new InventoryResponse("B200", 50, 50, 0, Instant.now())
        );
        when(service.listStock()).thenReturn(responses);

        var entity = controller.listStock();

        assertThat(entity.data()).isEqualTo(responses);
        assertThat(entity.error()).isNull();
    }
}
