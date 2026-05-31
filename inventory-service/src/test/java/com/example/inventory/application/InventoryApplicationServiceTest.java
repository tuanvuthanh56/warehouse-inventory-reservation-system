package com.example.inventory.application;

import com.example.common.messaging.ConfirmInventoryCommand;
import com.example.common.messaging.ReleaseInventoryCommand;
import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.ReserveInventoryCommand;
import com.example.inventory.api.error.ApiException;
import com.example.inventory.api.error.ConflictException;
import com.example.inventory.api.error.NotFoundException;
import com.example.inventory.domain.factory.InventoryHoldFactory;
import com.example.inventory.domain.model.InventoryHoldStatus;
import com.example.inventory.infrastructure.persistence.InboxMessageRepository;
import com.example.inventory.infrastructure.persistence.InventoryEntity;
import com.example.inventory.infrastructure.persistence.InventoryHoldEntity;
import com.example.inventory.infrastructure.persistence.InventoryHoldItemEntity;
import com.example.inventory.infrastructure.persistence.InventoryHoldRepository;
import com.example.inventory.infrastructure.persistence.InventoryRepository;
import com.example.inventory.infrastructure.persistence.OutboxEventEntity;
import com.example.inventory.infrastructure.persistence.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private final InventoryHoldFactory inventoryHoldFactory = new InventoryHoldFactory();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InventoryApplicationService service = new InventoryApplicationService(
            inventoryRepository,
            inventoryHoldRepository,
            outboxEventRepository,
            inboxMessageRepository,
            inventoryHoldFactory,
            objectMapper
    );

    @Test
    void getStockReturnsCurrentInventorySnapshot() {
        Instant updatedAt = Instant.now();
        when(inventoryRepository.findById("A100"))
                .thenReturn(Optional.of(new InventoryEntity("A100", 100, 80, 20, updatedAt)));

        var response = service.getStock("A100");

        assertThat(response.sku()).isEqualTo("A100");
        assertThat(response.onHandStock()).isEqualTo(100);
        assertThat(response.availableStock()).isEqualTo(80);
        assertThat(response.reservedStock()).isEqualTo(20);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void listStockReturnsInventorySnapshotsOrderedBySku() {
        Instant updatedAt = Instant.now();
        when(inventoryRepository.findAllByOrderBySkuAsc()).thenReturn(List.of(
                new InventoryEntity("A100", 100, 80, 20, updatedAt),
                new InventoryEntity("B200", 50, 50, 0, updatedAt)
        ));

        var responses = service.listStock();

        assertThat(responses).extracting("sku").containsExactly("A100", "B200");
        assertThat(responses.get(0).availableStock()).isEqualTo(80);
        assertThat(responses.get(1).onHandStock()).isEqualTo(50);
    }

    @Test
    void getStockThrowsNotFoundWhenSkuDoesNotExist() {
        when(inventoryRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStock("MISSING"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Inventory not found for SKU.");
    }

    @Test
    void reserveIgnoresAlreadyProcessedCommand() {
        var command = reserveCommand(UUID.randomUUID(), List.of(new ReservationItemMessage("A100", 1)));
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(true);

        service.reserve(command);

        verify(inventoryRepository, never()).findAllBySkuInForUpdate(anyCollection());
        verify(outboxEventRepository, never()).save(any());
    }

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
    void reserveMergesDuplicateSkusBeforeDeductingAndPublishing() throws Exception {
        UUID reservationId = UUID.randomUUID();
        InventoryEntity a100 = new InventoryEntity("A100", 100, 100, 0, Instant.now());
        when(inboxMessageRepository.existsById(any())).thenReturn(false);
        when(inventoryHoldRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllBySkuInForUpdate(anyCollection())).thenReturn(List.of(a100));

        service.reserve(new ReserveInventoryCommand(
                UUID.randomUUID(),
                reservationId,
                "ORD-DUP",
                List.of(new ReservationItemMessage("A100", 2), new ReservationItemMessage("A100", 3)),
                Instant.now()
        ));

        assertThat(a100.getAvailableStock()).isEqualTo(95);
        assertThat(a100.getReservedStock()).isEqualTo(5);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        var event = objectMapper.readTree(outboxCaptor.getValue().getPayload());
        assertThat(event.get("items").get(0).get("quantity").asInt()).isEqualTo(5);
    }

    @Test
    void reserveRejectsAllItemsWithoutPartialDeductionWhenAnySkuIsInsufficient() throws Exception {
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
        var event = objectMapper.readTree(outboxCaptor.getValue().getPayload());
        assertThat(event.get("reason").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(event.get("unavailableItems").get(0).get("sku").asText()).isEqualTo("B200");
        assertThat(event.get("unavailableItems").get(0).get("available").asInt()).isEqualTo(1);
        assertThat(event.get("unavailableItems").get(0).get("reason").asText()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void reserveRejectsUnknownSkuWithItemLevelReason() throws Exception {
        UUID reservationId = UUID.randomUUID();
        InventoryEntity a100 = new InventoryEntity("A100", 100, 100, 0, Instant.now());
        when(inboxMessageRepository.existsById(any())).thenReturn(false);
        when(inventoryHoldRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllBySkuInForUpdate(anyCollection())).thenReturn(List.of(a100));

        service.reserve(new ReserveInventoryCommand(
                UUID.randomUUID(),
                reservationId,
                "ORD-UNKNOWN",
                List.of(new ReservationItemMessage("A100", 10), new ReservationItemMessage("B500", 5)),
                Instant.now()
        ));

        assertThat(a100.getAvailableStock()).isEqualTo(100);
        assertThat(a100.getReservedStock()).isZero();
        verify(inventoryHoldRepository, never()).save(any());

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        var event = objectMapper.readTree(outboxCaptor.getValue().getPayload());
        assertThat(event.get("reason").asText()).isEqualTo("SKU_NOT_FOUND");
        assertThat(event.get("unavailableItems").get(0).get("sku").asText()).isEqualTo("B500");
        assertThat(event.get("unavailableItems").get(0).get("available").asInt()).isZero();
        assertThat(event.get("unavailableItems").get(0).get("reason").asText()).isEqualTo("SKU_NOT_FOUND");
    }

    @Test
    void reservePublishesExistingHoldResultWithoutDeductingAgain() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.HELD, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 4, Instant.now()));
        var command = reserveCommand(reservationId, List.of(new ReservationItemMessage("A100", 4)));
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false, true);
        when(inventoryHoldRepository.findByReservationId(reservationId)).thenReturn(Optional.of(hold));

        service.reserve(command);

        verify(inventoryRepository, never()).findAllBySkuInForUpdate(anyCollection());
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryReservedEvent");
    }

    @Test
    void confirmHeldHoldConsumesReservedStockAndPublishesEvent() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.HELD, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 5, Instant.now()));
        InventoryEntity inventory = new InventoryEntity("A100", 100, 95, 5, Instant.now());
        var command = new ConfirmInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.of(hold));
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventory));

        service.confirm(command);

        assertThat(inventory.getOnHandStock()).isEqualTo(95);
        assertThat(inventory.getAvailableStock()).isEqualTo(95);
        assertThat(inventory.getReservedStock()).isZero();
        assertThat(hold.getStatus()).isEqualTo(InventoryHoldStatus.CONFIRMED);
        assertOutboxEventType("InventoryConfirmedEvent");
    }

    @Test
    void confirmAlreadyConfirmedHoldPublishesEventWithoutStockChange() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.CONFIRMED, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 5, Instant.now()));
        var command = new ConfirmInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.of(hold));

        service.confirm(command);

        verify(inventoryRepository, never()).findAllBySkuInForUpdate(anyCollection());
        assertOutboxEventType("InventoryConfirmedEvent");
    }

    @Test
    void confirmReleasedHoldRecordsInboxWithoutPublishing() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.RELEASED, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 5, Instant.now()));
        var command = new ConfirmInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.of(hold));

        service.confirm(command);

        verify(outboxEventRepository, never()).save(any());
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void confirmThrowsConflictWhenHoldIsMissing() {
        UUID reservationId = UUID.randomUUID();
        var command = new ConfirmInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Inventory hold not found.");
    }

    @Test
    void releaseHeldHoldReturnsStockAndPublishesEvent() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.HELD, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 5, Instant.now()));
        InventoryEntity inventory = new InventoryEntity("A100", 100, 95, 5, Instant.now());
        var command = new ReleaseInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", "CLIENT_CANCELLED", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.of(hold));
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventory));

        service.release(command);

        assertThat(inventory.getOnHandStock()).isEqualTo(100);
        assertThat(inventory.getAvailableStock()).isEqualTo(100);
        assertThat(inventory.getReservedStock()).isZero();
        assertThat(hold.getStatus()).isEqualTo(InventoryHoldStatus.RELEASED);
        assertOutboxEventType("InventoryReleasedEvent");
    }

    @Test
    void releaseAlreadyReleasedHoldPublishesEventWithoutStockChange() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.RELEASED, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 5, Instant.now()));
        var command = new ReleaseInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", "CLIENT_CANCELLED", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.of(hold));

        service.release(command);

        verify(inventoryRepository, never()).findAllBySkuInForUpdate(anyCollection());
        assertOutboxEventType("InventoryReleasedEvent");
    }

    @Test
    void releaseConfirmedHoldRecordsInboxWithoutPublishing() {
        UUID reservationId = UUID.randomUUID();
        var hold = hold(reservationId, InventoryHoldStatus.CONFIRMED, new InventoryHoldItemEntity(UUID.randomUUID(), "A100", 5, Instant.now()));
        var command = new ReleaseInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", "CLIENT_CANCELLED", Instant.now());
        when(inboxMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(inventoryHoldRepository.findWithLockByReservationId(reservationId)).thenReturn(Optional.of(hold));

        service.release(command);

        verify(outboxEventRepository, never()).save(any());
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void saveOutboxWrapsJsonSerializationFailure() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        var failingService = new InventoryApplicationService(
                inventoryRepository,
                inventoryHoldRepository,
                outboxEventRepository,
                inboxMessageRepository,
                inventoryHoldFactory,
                failingMapper
        );
        UUID reservationId = UUID.randomUUID();
        InventoryEntity inventory = new InventoryEntity("A100", 100, 100, 0, Instant.now());
        when(inboxMessageRepository.existsById(any())).thenReturn(false);
        when(inventoryHoldRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllBySkuInForUpdate(anyCollection())).thenReturn(List.of(inventory));

        assertThatThrownBy(() -> failingService.reserve(reserveCommand(reservationId, List.of(new ReservationItemMessage("A100", 1)))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Unable to serialize message payload.");
    }

    private ReserveInventoryCommand reserveCommand(UUID reservationId, List<ReservationItemMessage> items) {
        return new ReserveInventoryCommand(UUID.randomUUID(), reservationId, "ORD-1", items, Instant.now());
    }

    private InventoryHoldEntity hold(UUID reservationId, InventoryHoldStatus status, InventoryHoldItemEntity item) {
        var hold = new InventoryHoldEntity(UUID.randomUUID(), reservationId, "ORD-1", status, Instant.now());
        hold.addItem(item);
        return hold;
    }

    private void assertOutboxEventType(String eventType) {
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(eventType);
    }
}
