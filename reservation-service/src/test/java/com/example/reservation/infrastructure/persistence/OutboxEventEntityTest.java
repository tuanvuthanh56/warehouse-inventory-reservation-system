package com.example.reservation.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventEntityTest {
    @Test
    void newEventStoresIdTypeAndPayloadAndCanBeMarkedPublished() {
        var event = new OutboxEventEntity(UUID.randomUUID(), "ReserveInventoryCommand", "{}");

        assertThat(event.getId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("ReserveInventoryCommand");
        assertThat(event.getPayload()).isEqualTo("{}");

        event.markPublished();
        assertThat(event.getEventType()).isEqualTo("ReserveInventoryCommand");
    }
}
