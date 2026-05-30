package com.example.inventory.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryEntityTest {
    @Test
    void reserveConfirmAndReleaseAdjustStockCorrectly() {
        InventoryEntity inventory = new InventoryEntity("A100", 100, 100, 0, Instant.now());

        inventory.reserve(30);
        assertThat(inventory.getOnHandStock()).isEqualTo(100);
        assertThat(inventory.getAvailableStock()).isEqualTo(70);
        assertThat(inventory.getReservedStock()).isEqualTo(30);

        inventory.confirm(10);
        assertThat(inventory.getOnHandStock()).isEqualTo(90);
        assertThat(inventory.getAvailableStock()).isEqualTo(70);
        assertThat(inventory.getReservedStock()).isEqualTo(20);

        inventory.release(20);
        assertThat(inventory.getOnHandStock()).isEqualTo(90);
        assertThat(inventory.getAvailableStock()).isEqualTo(90);
        assertThat(inventory.getReservedStock()).isZero();
    }
}
