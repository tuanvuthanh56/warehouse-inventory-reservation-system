package com.example.inventory.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventEntityTest {
    @Test
    void newEventStoresTypeAndPayloadAndCanBeMarkedPublished() {
        var event = new OutboxEventEntity(UUID.randomUUID(), "InventoryReservedEvent", "{}");

        assertThat(event.getEventType()).isEqualTo("InventoryReservedEvent");
        assertThat(event.getPayload()).isEqualTo("{}");

        event.markPublished();
        assertThat(event.getEventType()).isEqualTo("InventoryReservedEvent");
    }
}
