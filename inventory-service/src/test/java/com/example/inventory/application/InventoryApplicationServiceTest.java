package com.example.inventory.application;

import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.ReserveInventoryCommand;
import com.example.inventory.infrastructure.persistence.InboxMessageRepository;
import com.example.inventory.infrastructure.persistence.InventoryEntity;
import com.example.inventory.infrastructure.persistence.InventoryHoldRepository;
import com.example.inventory.infrastructure.persistence.InventoryRepository;
import com.example.inventory.infrastructure.persistence.OutboxEventEntity;
import com.example.inventory.infrastructure.persistence.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceTest {
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final InventoryHoldRepository inventoryHoldRepository = mock(InventoryHoldRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final InboxMessageRepository inboxMessageRepository = mock(InboxMessageRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InventoryApplicationService service = new InventoryApplicationService(
            inventoryRepository,
            inventoryHoldRepository,
            outboxEventRepository,
            inboxMessageRepository,
            objectMapper
    );

    @Test
    void reserveDeductsAllSkusAtomicallyWhenStockIsAvailable() {
        UUID reservationId = UUID.randomUUID();
        InventoryEntity a100 = new InventoryEntity("A100", 100, 100, 0, Instant.now());
        InventoryEntity b200 = new InventoryEntity("B200", 50, 50, 0, Instant.now());
        when(inboxMessageRepository.existsById(any())).thenReturn(false);
        when(inventoryHoldRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllBySkuInForUpdate(anyCollection())).thenReturn(List.of(a100, b200));

        service.reserve(new ReserveInventoryCommand(
                UUID.randomUUID(),
                reservationId,
                "ORD-1001",
                List.of(new ReservationItemMessage("A100", 5), new ReservationItemMessage("B200", 3)),
                Instant.now()
        ));

        assertThat(a100.getAvailableStock()).isEqualTo(95);
        assertThat(a100.getReservedStock()).isEqualTo(5);
        assertThat(b200.getAvailableStock()).isEqualTo(47);
        assertThat(b200.getReservedStock()).isEqualTo(3);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryReservedEvent");
    }

    @Test
    void reserveRejectsAllItemsWithoutPartialDeductionWhenAnySkuIsInsufficient() {
        UUID reservationId = UUID.randomUUID();
        InventoryEntity a100 = new InventoryEntity("A100", 100, 100, 0, Instant.now());
        InventoryEntity b200 = new InventoryEntity("B200", 50, 1, 0, Instant.now());
        when(inboxMessageRepository.existsById(any())).thenReturn(false);
        when(inventoryHoldRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllBySkuInForUpdate(anyCollection())).thenReturn(List.of(a100, b200));

        service.reserve(new ReserveInventoryCommand(
                UUID.randomUUID(),
                reservationId,
                "ORD-1002",
                List.of(new ReservationItemMessage("A100", 10), new ReservationItemMessage("B200", 5)),
                Instant.now()
        ));

        assertThat(a100.getAvailableStock()).isEqualTo(100);
        assertThat(a100.getReservedStock()).isZero();
        assertThat(b200.getAvailableStock()).isEqualTo(1);
        assertThat(b200.getReservedStock()).isZero();
        verify(inventoryHoldRepository, never()).save(any());

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryReservationRejectedEvent");
    }
}
