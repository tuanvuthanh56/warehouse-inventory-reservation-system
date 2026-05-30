package com.example.inventory.infrastructure.persistence;

import com.example.inventory.domain.model.InventoryHoldStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryHoldEntityTest {
    @Test
    void addItemAndStatusChangesMaintainHoldLifecycle() {
        Instant createdAt = Instant.now();
        var hold = new InventoryHoldEntity(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", InventoryHoldStatus.HELD, createdAt);
        var item = new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 2, createdAt);

        hold.addItem(item);
        hold.markConfirmed();

        assertThat(hold.getItems()).containsExactly(item);
        assertThat(hold.getStatus()).isEqualTo(InventoryHoldStatus.CONFIRMED);
        assertThat(hold.getOrderId()).isEqualTo("ORD-1");
        assertThat(item.getSku()).isEqualTo("A100");
        assertThat(item.getQuantity()).isEqualTo(2);

        hold.markReleased();
        assertThat(hold.getStatus()).isEqualTo(InventoryHoldStatus.RELEASED);
    }
}
