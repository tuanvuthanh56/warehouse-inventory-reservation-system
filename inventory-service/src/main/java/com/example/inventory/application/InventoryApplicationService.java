package com.example.inventory.application;

import com.example.common.api.ErrorCode;
import com.example.common.messaging.ConfirmInventoryCommand;
import com.example.common.messaging.InventoryConfirmedEvent;
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservationRejectedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.ReleaseInventoryCommand;
import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.ReserveInventoryCommand;
import com.example.common.messaging.UnavailableItemMessage;
import com.example.inventory.api.dto.InventoryResponse;
import com.example.inventory.api.error.ApiException;
import com.example.inventory.api.error.ConflictException;
import com.example.inventory.api.error.NotFoundException;
import com.example.inventory.domain.factory.InventoryHoldFactory;
import com.example.inventory.domain.model.InventoryHoldStatus;
import com.example.inventory.infrastructure.persistence.InboxMessageEntity;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns all stock mutations and keeps reservation, confirmation, and release flows idempotent.
 */
@Service
public class InventoryApplicationService {

    private final InventoryRepository inventoryRepository;

    private final InventoryHoldRepository inventoryHoldRepository;

    private final OutboxEventRepository outboxEventRepository;

    private final InboxMessageRepository inboxMessageRepository;

    private final InventoryHoldFactory inventoryHoldFactory;

    private final ObjectMapper objectMapper;

    public InventoryApplicationService(
            InventoryRepository inventoryRepository,
            InventoryHoldRepository inventoryHoldRepository,
            OutboxEventRepository outboxEventRepository,
            InboxMessageRepository inboxMessageRepository,
            InventoryHoldFactory inventoryHoldFactory,
            ObjectMapper objectMapper
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
        this.inventoryHoldFactory = inventoryHoldFactory;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getStock(String sku) {
        InventoryEntity inventory = inventoryRepository.findById(sku)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.SKU_NOT_FOUND,
                        "Inventory not found for SKU.",
                        Map.of("sku", sku)
                ));
        return toResponse(inventory);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> listStock() {
        return inventoryRepository.findAllByOrderBySkuAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Reserves all requested SKUs atomically, or rejects the entire request.
     */
    @Transactional
    public void reserve(ReserveInventoryCommand command) {
        // RabbitMQ may redeliver commands; the inbox table prevents duplicate side effects.
        if (alreadyProcessed(command.messageId())) {
            return;
        }

        // A repeated reservation id must replay the prior result, not reserve stock again.
        inventoryHoldRepository.findByReservationId(command.reservationId()).ifPresent(existing -> {
            saveReservedEvent(command, existing);
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReserveInventoryCommand.class.getSimpleName()));
        });

        if (alreadyProcessed(command.messageId())) {
            return;
        }

        // The factory validates items, merges duplicate SKUs, and creates the initial HELD hold.
        InventoryHoldEntity hold = inventoryHoldFactory.createHeldHold(command.reservationId(), command.orderId(), command.items());
        List<String> skus = hold.getItems().stream().map(InventoryHoldItemEntity::getSku).sorted().toList();

        // Pessimistic row locks prevent concurrent reservations from overselling the same SKU.
        Map<String, InventoryEntity> inventoryBySku = inventoryRepository.findAllBySkuInForUpdate(skus).stream()
                .collect(Collectors.toMap(InventoryEntity::getSku, Function.identity()));

        // Validate the complete request before mutating stock so there is no partial reservation.
        List<UnavailableItemMessage> unavailableItems = new ArrayList<>();
        for (InventoryHoldItemEntity item : hold.getItems()) {
            InventoryEntity inventory = inventoryBySku.get(item.getSku());
            int available = inventory == null ? 0 : inventory.getAvailableStock();
            if (inventory == null || available < item.getQuantity()) {
                unavailableItems.add(new UnavailableItemMessage(item.getSku(), item.getQuantity(), available));
            }
        }

        if (!unavailableItems.isEmpty()) {
            InventoryReservationRejectedEvent event = new InventoryReservationRejectedEvent(
                    UUID.randomUUID(),
                    command.reservationId(),
                    command.orderId(),
                    "INSUFFICIENT_STOCK",
                    unavailableItems,
                    Instant.now()
            );
            saveOutbox(command.reservationId(), InventoryReservationRejectedEvent.class.getSimpleName(), event);
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReserveInventoryCommand.class.getSimpleName()));
            return;
        }

        for (InventoryHoldItemEntity item : hold.getItems()) {
            inventoryBySku.get(item.getSku()).reserve(item.getQuantity());
        }
        inventoryHoldRepository.save(hold);
        saveReservedEvent(command, hold);
        inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReserveInventoryCommand.class.getSimpleName()));
    }

    /**
     * Consumes previously held stock after the reservation is confirmed.
     */
    @Transactional
    public void confirm(ConfirmInventoryCommand command) {
        if (alreadyProcessed(command.messageId())) {
            return;
        }

        // Lock the hold first so confirm and release cannot win the same reservation concurrently.
        InventoryHoldEntity hold = inventoryHoldRepository.findWithLockByReservationId(command.reservationId())
                .orElseThrow(() -> new ConflictException(ErrorCode.CONFLICT, "Inventory hold not found.", Map.of("reservationId", command.reservationId())));

        // Replaying a completed command publishes the same outcome without changing stock twice.
        if (hold.getStatus() == InventoryHoldStatus.CONFIRMED) {
            saveConfirmedEvent(command);
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ConfirmInventoryCommand.class.getSimpleName()));
            return;
        }

        if (hold.getStatus() == InventoryHoldStatus.RELEASED) {
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ConfirmInventoryCommand.class.getSimpleName()));
            return;
        }

        Map<String, InventoryEntity> inventoryBySku = lockInventoryForHold(hold);
        for (InventoryHoldItemEntity item : hold.getItems()) {
            inventoryBySku.get(item.getSku()).confirm(item.getQuantity());
        }
        hold.markConfirmed();
        saveConfirmedEvent(command);
        inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ConfirmInventoryCommand.class.getSimpleName()));
    }

    /**
     * Returns held stock to availability after the reservation is cancelled.
     */
    @Transactional
    public void release(ReleaseInventoryCommand command) {
        if (alreadyProcessed(command.messageId())) {
            return;
        }

        // Lock the hold first so confirm and release cannot win the same reservation concurrently.
        InventoryHoldEntity hold = inventoryHoldRepository.findWithLockByReservationId(command.reservationId())
                .orElseThrow(() -> new ConflictException(ErrorCode.CONFLICT, "Inventory hold not found.", Map.of("reservationId", command.reservationId())));

        // Replaying a completed command publishes the same outcome without changing stock twice.
        if (hold.getStatus() == InventoryHoldStatus.RELEASED) {
            saveReleasedEvent(command);
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReleaseInventoryCommand.class.getSimpleName()));
            return;
        }

        if (hold.getStatus() == InventoryHoldStatus.CONFIRMED) {
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReleaseInventoryCommand.class.getSimpleName()));
            return;
        }

        Map<String, InventoryEntity> inventoryBySku = lockInventoryForHold(hold);
        for (InventoryHoldItemEntity item : hold.getItems()) {
            inventoryBySku.get(item.getSku()).release(item.getQuantity());
        }
        hold.markReleased();
        saveReleasedEvent(command);
        inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReleaseInventoryCommand.class.getSimpleName()));
    }

    private void saveReservedEvent(ReserveInventoryCommand command, InventoryHoldEntity hold) {
        InventoryReservedEvent event = new InventoryReservedEvent(
                UUID.randomUUID(),
                command.reservationId(),
                command.orderId(),
                hold.getId(),
                hold.getItems().stream()
                        .map(item -> new ReservationItemMessage(item.getSku(), item.getQuantity()))
                        .toList(),
                Instant.now()
        );
        saveOutbox(command.reservationId(), InventoryReservedEvent.class.getSimpleName(), event);
    }

    private void saveConfirmedEvent(ConfirmInventoryCommand command) {
        InventoryConfirmedEvent event = new InventoryConfirmedEvent(UUID.randomUUID(), command.reservationId(), command.orderId(), Instant.now());
        saveOutbox(command.reservationId(), InventoryConfirmedEvent.class.getSimpleName(), event);
    }

    private void saveReleasedEvent(ReleaseInventoryCommand command) {
        InventoryReleasedEvent event = new InventoryReleasedEvent(UUID.randomUUID(), command.reservationId(), command.orderId(), Instant.now());
        saveOutbox(command.reservationId(), InventoryReleasedEvent.class.getSimpleName(), event);
    }

    /**
     * Stores an integration event for the scheduled publisher to send after transaction commit.
     */
    private void saveOutbox(UUID aggregateId, String eventType, Object payload) {
        try {
            outboxEventRepository.save(new OutboxEventEntity(aggregateId, eventType, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.MESSAGE_PROCESSING_FAILED, "Unable to serialize message payload.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Locks hold SKUs in sorted order to reduce deadlock risk across concurrent transactions.
     */
    private Map<String, InventoryEntity> lockInventoryForHold(InventoryHoldEntity hold) {
        List<String> skus = hold.getItems().stream().map(InventoryHoldItemEntity::getSku).sorted().toList();
        return inventoryRepository.findAllBySkuInForUpdate(skus).stream()
                .collect(Collectors.toMap(InventoryEntity::getSku, Function.identity()));
    }

    private boolean alreadyProcessed(UUID messageId) {
        return inboxMessageRepository.existsById(messageId);
    }

    private InventoryResponse toResponse(InventoryEntity inventory) {
        return new InventoryResponse(
                inventory.getSku(),
                inventory.getOnHandStock(),
                inventory.getAvailableStock(),
                inventory.getReservedStock(),
                inventory.getUpdatedAt()
        );
    }
}
