package com.example.inventory.domain.factory;

import com.example.common.messaging.ReservationItemMessage;
import com.example.inventory.domain.exception.InventoryDomainException;
import com.example.inventory.domain.model.InventoryHoldStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryHoldFactoryTest {
    private final InventoryHoldFactory factory = new InventoryHoldFactory();

    @Test
    void createsHeldHoldAndMergesDuplicateSkus() {
        UUID reservationId = UUID.randomUUID();

        var hold = factory.createHeldHold(reservationId, " ORD-1001 ", List.of(
                new ReservationItemMessage("B200", 1),
                new ReservationItemMessage("A100", 2),
                new ReservationItemMessage("A100", 3)
        ));

        assertThat(hold.getReservationId()).isEqualTo(reservationId);
        assertThat(hold.getOrderId()).isEqualTo("ORD-1001");
        assertThat(hold.getStatus()).isEqualTo(InventoryHoldStatus.HELD);
        assertThat(hold.getItems()).extracting("sku", "quantity")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A100", 5),
                        org.assertj.core.groups.Tuple.tuple("B200", 1)
                );
    }

    @Test
    void rejectsInvalidHoldData() {
        UUID reservationId = UUID.randomUUID();

        assertThatThrownBy(() -> factory.createHeldHold(null, "ORD-1", List.of(new ReservationItemMessage("A100", 1))))
                .isInstanceOf(InventoryDomainException.class);
        assertThatThrownBy(() -> factory.createHeldHold(reservationId, " ", List.of(new ReservationItemMessage("A100", 1))))
                .isInstanceOf(InventoryDomainException.class);
        assertThatThrownBy(() -> factory.createHeldHold(reservationId, "ORD-1", null))
                .isInstanceOf(InventoryDomainException.class);
        assertThatThrownBy(() -> factory.createHeldHold(reservationId, "ORD-1", List.of()))
                .isInstanceOf(InventoryDomainException.class);
        assertThatThrownBy(() -> factory.createHeldHold(reservationId, "ORD-1", List.of(new ReservationItemMessage(" ", 1))))
                .isInstanceOf(InventoryDomainException.class);
        assertThatThrownBy(() -> factory.createHeldHold(reservationId, "ORD-1", List.of(new ReservationItemMessage(null, 1))))
                .isInstanceOf(InventoryDomainException.class);
        assertThatThrownBy(() -> factory.createHeldHold(reservationId, "ORD-1", List.of(new ReservationItemMessage("A100", 0))))
                .isInstanceOf(InventoryDomainException.class);
    }
}
