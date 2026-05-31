package com.example.reservation.application;

import com.example.common.api.ErrorCode;
import com.example.common.messaging.ConfirmInventoryCommand;
import com.example.common.messaging.InventoryConfirmedEvent;
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservationRejectedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.ReleaseInventoryCommand;
import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.ReserveInventoryCommand;
import com.example.reservation.api.dto.CreateReservationRequest;
import com.example.reservation.api.dto.ReservationItemResponse;
import com.example.reservation.api.dto.ReservationResponse;
import com.example.reservation.api.error.ApiException;
import com.example.reservation.api.error.ConflictException;
import com.example.reservation.api.error.NotFoundException;
import com.example.reservation.domain.factory.ReservationFactory;
import com.example.reservation.domain.model.Reservation;
import com.example.reservation.domain.model.ReservationItem;
import com.example.reservation.domain.model.ReservationStatus;
import com.example.reservation.domain.state.ReservationStateMachine;
import com.example.reservation.infrastructure.persistence.InboxMessageEntity;
import com.example.reservation.infrastructure.persistence.InboxMessageRepository;
import com.example.reservation.infrastructure.persistence.OutboxEventEntity;
import com.example.reservation.infrastructure.persistence.OutboxEventRepository;
import com.example.reservation.infrastructure.persistence.ReservationEntity;
import com.example.reservation.infrastructure.persistence.ReservationItemEntity;
import com.example.reservation.infrastructure.persistence.ReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates reservation use cases and the asynchronous inventory workflow.
 */
@Service
public class ReservationApplicationService {

    private final ReservationFactory reservationFactory;

    private final ReservationStateMachine stateMachine;

    private final ReservationRepository reservationRepository;

    private final OutboxEventRepository outboxEventRepository;

    private final InboxMessageRepository inboxMessageRepository;

    private final ObjectMapper objectMapper;

    public ReservationApplicationService(
            ReservationFactory reservationFactory,
            ReservationStateMachine stateMachine,
            ReservationRepository reservationRepository,
            OutboxEventRepository outboxEventRepository,
            InboxMessageRepository inboxMessageRepository,
            ObjectMapper objectMapper
    ) {
        this.reservationFactory = reservationFactory;
        this.stateMachine = stateMachine;
        this.reservationRepository = reservationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a reservation once per order id and stores the inventory reserve command in the outbox.
     */
    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        return reservationRepository.findByOrderId(request.orderId().trim())
                .map(this::toResponse)
                .orElseGet(() -> createNewReservation(request));
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        return reservationRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.RESERVATION_NOT_FOUND,
                        "Reservation not found.",
                        Map.of("reservationId", id)
                ));
    }

    /**
     * Moves a PENDING reservation to CONFIRMING and requests inventory confirmation asynchronously.
     */
    @Transactional
    public ReservationResponse confirm(UUID id) {
        ReservationEntity reservation = findReservation(id);
        if (!stateMachine.canTransition(reservation.getStatus(), ReservationStatus.CONFIRMING)) {
            throw notPending(id, reservation.getStatus(), "Only PENDING reservations can be confirmed.");
        }
        reservation.transitionTo(stateMachine.confirm(reservation.getStatus()), null);

        // The stock mutation belongs to Inventory Service; this transaction only records the command.
        ConfirmInventoryCommand command = new ConfirmInventoryCommand(UUID.randomUUID(), reservation.getId(), reservation.getOrderId(), Instant.now());
        outboxEventRepository.save(new OutboxEventEntity(reservation.getId(), ConfirmInventoryCommand.class.getSimpleName(), writeJson(command)));
        return toResponse(reservation);
    }

    /**
     * Moves a PENDING reservation to CANCELLING and requests inventory release asynchronously.
     */
    @Transactional
    public ReservationResponse cancel(UUID id) {
        ReservationEntity reservation = findReservation(id);
        if (!stateMachine.canTransition(reservation.getStatus(), ReservationStatus.CANCELLING)) {
            throw notPending(id, reservation.getStatus(), "Only PENDING reservations can be cancelled.");
        }
        reservation.transitionTo(stateMachine.cancel(reservation.getStatus()), null);
        ReleaseInventoryCommand command = new ReleaseInventoryCommand(UUID.randomUUID(), reservation.getId(), reservation.getOrderId(), "CANCELLED_BY_CLIENT", Instant.now());
        outboxEventRepository.save(new OutboxEventEntity(reservation.getId(), ReleaseInventoryCommand.class.getSimpleName(), writeJson(command)));
        return toResponse(reservation);
    }

    /**
     * Applies the success event for the reserve step.
     */
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        if (alreadyProcessed(event.messageId())) {
            return;
        }
        ReservationEntity reservation = findReservation(event.reservationId());

        // Ignore duplicate or late events that no longer match the expected state.
        if (reservation.getStatus() == ReservationStatus.RESERVING) {
            reservation.transitionTo(ReservationStatus.PENDING, null);
        }
        inboxMessageRepository.save(new InboxMessageEntity(event.messageId(), InventoryReservedEvent.class.getSimpleName()));
    }

    /**
     * Applies the failure event for the reserve step.
     */
    @Transactional
    public void handleInventoryRejected(InventoryReservationRejectedEvent event) {
        if (alreadyProcessed(event.messageId())) {
            return;
        }
        ReservationEntity reservation = findReservation(event.reservationId());
        if (reservation.getStatus() == ReservationStatus.RESERVING) {
            reservation.transitionTo(ReservationStatus.REJECTED, event.reason());
        }
        inboxMessageRepository.save(new InboxMessageEntity(event.messageId(), InventoryReservationRejectedEvent.class.getSimpleName()));
    }

    /**
     * Applies the success event for the confirm step.
     */
    @Transactional
    public void handleInventoryConfirmed(InventoryConfirmedEvent event) {
        if (alreadyProcessed(event.messageId())) {
            return;
        }
        ReservationEntity reservation = findReservation(event.reservationId());
        if (reservation.getStatus() == ReservationStatus.CONFIRMING) {
            reservation.transitionTo(ReservationStatus.CONFIRMED, null);
        }
        inboxMessageRepository.save(new InboxMessageEntity(event.messageId(), InventoryConfirmedEvent.class.getSimpleName()));
    }

    /**
     * Applies the success event for the cancel/release step.
     */
    @Transactional
    public void handleInventoryReleased(InventoryReleasedEvent event) {
        if (alreadyProcessed(event.messageId())) {
            return;
        }
        ReservationEntity reservation = findReservation(event.reservationId());
        if (reservation.getStatus() == ReservationStatus.CANCELLING) {
            reservation.transitionTo(ReservationStatus.CANCELLED, null);
        }
        inboxMessageRepository.save(new InboxMessageEntity(event.messageId(), InventoryReleasedEvent.class.getSimpleName()));
    }

    /**
     * Persists the reservation and outbox command in the same transaction.
     */
    private ReservationResponse createNewReservation(CreateReservationRequest request) {
        Reservation reservation = reservationFactory.create(
                request.orderId(),
                request.items().stream()
                        .map(item -> new ReservationItem(item.sku(), item.quantity()))
                        .toList()
        );

        ReservationEntity entity = new ReservationEntity(
                reservation.id(),
                reservation.orderId(),
                reservation.status(),
                null,
                reservation.createdAt(),
                reservation.updatedAt()
        );

        for (ReservationItem item : reservation.items()) {
            entity.addItem(new ReservationItemEntity(UUID.randomUUID(), item.sku(), item.quantity(), reservation.createdAt()));
        }

        reservationRepository.save(entity);

        // Outbox keeps DB state and message publication consistent across process crashes.
        ReserveInventoryCommand command = new ReserveInventoryCommand(
                UUID.randomUUID(),
                reservation.id(),
                reservation.orderId(),
                reservation.items().stream()
                        .map(item -> new ReservationItemMessage(item.sku(), item.quantity()))
                        .toList(),
                Instant.now()
        );
        outboxEventRepository.save(new OutboxEventEntity(reservation.id(), ReserveInventoryCommand.class.getSimpleName(), writeJson(command)));
        return toResponse(entity);
    }

    private ReservationEntity findReservation(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.RESERVATION_NOT_FOUND,
                        "Reservation not found.",
                        Map.of("reservationId", id)
                ));
    }

    /**
     * Checks the inbox table to make inventory events idempotent.
     */
    private boolean alreadyProcessed(UUID messageId) {
        return inboxMessageRepository.existsById(messageId);
    }

    private ConflictException notPending(UUID reservationId, ReservationStatus currentStatus, String message) {
        return new ConflictException(
                ErrorCode.RESERVATION_NOT_PENDING,
                message,
                Map.of("reservationId", reservationId, "currentStatus", currentStatus.name())
        );
    }

    /**
     * Serializes a message payload before it is stored in the outbox table.
     */
    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.MESSAGE_PROCESSING_FAILED, "Unable to serialize message payload.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ReservationResponse toResponse(ReservationEntity entity) {
        List<ReservationItemResponse> items = entity.getItems().stream()
                .map(item -> new ReservationItemResponse(item.getSku(), item.getQuantity()))
                .toList();
        return new ReservationResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getStatus().name(),
                entity.getFailureReason(),
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
