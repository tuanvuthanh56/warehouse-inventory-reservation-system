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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryApplicationService {
    private final InventoryRepository inventoryRepository;
    private final InventoryHoldRepository inventoryHoldRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InboxMessageRepository inboxMessageRepository;
    private final ObjectMapper objectMapper;

    public InventoryApplicationService(
            InventoryRepository inventoryRepository,
            InventoryHoldRepository inventoryHoldRepository,
            OutboxEventRepository outboxEventRepository,
            InboxMessageRepository inboxMessageRepository,
            ObjectMapper objectMapper
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
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
        return new InventoryResponse(
                inventory.getSku(),
                inventory.getOnHandStock(),
                inventory.getAvailableStock(),
                inventory.getReservedStock(),
                inventory.getUpdatedAt()
        );
    }

    @Transactional
    public void reserve(ReserveInventoryCommand command) {
        if (alreadyProcessed(command.messageId())) {
            return;
        }
        inventoryHoldRepository.findByReservationId(command.reservationId()).ifPresent(existing -> {
            saveReservedEvent(command, existing);
            inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReserveInventoryCommand.class.getSimpleName()));
        });
        if (alreadyProcessed(command.messageId())) {
            return;
        }

        List<ReservationItemMessage> items = mergeItems(command.items());
        List<String> skus = items.stream().map(ReservationItemMessage::sku).sorted().toList();
        Map<String, InventoryEntity> inventoryBySku = inventoryRepository.findAllBySkuInForUpdate(skus).stream()
                .collect(Collectors.toMap(InventoryEntity::getSku, Function.identity()));

        List<UnavailableItemMessage> unavailableItems = new ArrayList<>();
        for (ReservationItemMessage item : items) {
            InventoryEntity inventory = inventoryBySku.get(item.sku());
            int available = inventory == null ? 0 : inventory.getAvailableStock();
            if (inventory == null || available < item.quantity()) {
                unavailableItems.add(new UnavailableItemMessage(item.sku(), item.quantity(), available));
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

        Instant now = Instant.now();
        InventoryHoldEntity hold = new InventoryHoldEntity(UUID.randomUUID(), command.reservationId(), command.orderId(), InventoryHoldStatus.HELD, now);
        for (ReservationItemMessage item : items) {
            inventoryBySku.get(item.sku()).reserve(item.quantity());
            hold.addItem(new InventoryHoldItemEntity(UUID.randomUUID(), item.sku(), item.quantity(), now));
        }
        inventoryHoldRepository.save(hold);
        saveReservedEvent(command, hold);
        inboxMessageRepository.save(new InboxMessageEntity(command.messageId(), ReserveInventoryCommand.class.getSimpleName()));
    }

    @Transactional
    public void confirm(ConfirmInventoryCommand command) {
        if (alreadyProcessed(command.messageId())) {
            return;
        }
        InventoryHoldEntity hold = inventoryHoldRepository.findWithLockByReservationId(command.reservationId())
                .orElseThrow(() -> new ConflictException(ErrorCode.CONFLICT, "Inventory hold not found.", Map.of("reservationId", command.reservationId())));

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

    @Transactional
    public void release(ReleaseInventoryCommand command) {
        if (alreadyProcessed(command.messageId())) {
            return;
        }
        InventoryHoldEntity hold = inventoryHoldRepository.findWithLockByReservationId(command.reservationId())
                .orElseThrow(() -> new ConflictException(ErrorCode.CONFLICT, "Inventory hold not found.", Map.of("reservationId", command.reservationId())));

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

    private void saveOutbox(UUID aggregateId, String eventType, Object payload) {
        try {
            outboxEventRepository.save(new OutboxEventEntity(aggregateId, eventType, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.MESSAGE_PROCESSING_FAILED, "Unable to serialize message payload.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, InventoryEntity> lockInventoryForHold(InventoryHoldEntity hold) {
        List<String> skus = hold.getItems().stream().map(InventoryHoldItemEntity::getSku).sorted().toList();
        return inventoryRepository.findAllBySkuInForUpdate(skus).stream()
                .collect(Collectors.toMap(InventoryEntity::getSku, Function.identity()));
    }

    private List<ReservationItemMessage> mergeItems(List<ReservationItemMessage> items) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        items.stream()
                .sorted(Comparator.comparing(ReservationItemMessage::sku))
                .forEach(item -> merged.merge(item.sku(), item.quantity(), Integer::sum));
        return merged.entrySet().stream()
                .map(entry -> new ReservationItemMessage(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean alreadyProcessed(UUID messageId) {
        return inboxMessageRepository.existsById(messageId);
    }
}
