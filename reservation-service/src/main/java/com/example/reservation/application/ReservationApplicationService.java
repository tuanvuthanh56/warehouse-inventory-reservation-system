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
import com.example.reservation.domain.exception.ReservationDomainException;
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

    @Transactional
    public ReservationResponse confirm(UUID id) {
        ReservationEntity reservation = findReservation(id);
        try {
            reservation.transitionTo(stateMachine.confirm(reservation.getStatus()), null);
        } catch (ReservationDomainException ex) {
            throw notPending(id, reservation.getStatus(), "Only PENDING reservations can be confirmed.");
        }
        ConfirmInventoryCommand command = new ConfirmInventoryCommand(UUID.randomUUID(), reservation.getId(), reservation.getOrderId(), Instant.now());
        outboxEventRepository.save(new OutboxEventEntity(reservation.getId(), ConfirmInventoryCommand.class.getSimpleName(), writeJson(command)));
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(UUID id) {
        ReservationEntity reservation = findReservation(id);
        try {
            reservation.transitionTo(stateMachine.cancel(reservation.getStatus()), null);
        } catch (ReservationDomainException ex) {
            throw notPending(id, reservation.getStatus(), "Only PENDING reservations can be cancelled.");
        }
        ReleaseInventoryCommand command = new ReleaseInventoryCommand(UUID.randomUUID(), reservation.getId(), reservation.getOrderId(), "CANCELLED_BY_CLIENT", Instant.now());
        outboxEventRepository.save(new OutboxEventEntity(reservation.getId(), ReleaseInventoryCommand.class.getSimpleName(), writeJson(command)));
        return toResponse(reservation);
    }

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        if (alreadyProcessed(event.messageId())) {
            return;
        }
        ReservationEntity reservation = findReservation(event.reservationId());
        if (reservation.getStatus() == ReservationStatus.RESERVING) {
            reservation.transitionTo(ReservationStatus.PENDING, null);
        }
        inboxMessageRepository.save(new InboxMessageEntity(event.messageId(), InventoryReservedEvent.class.getSimpleName()));
    }

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

    private ReservationResponse createNewReservation(CreateReservationRequest request) {
        Reservation reservation;
        try {
            reservation = reservationFactory.create(
                    request.orderId(),
                    request.items().stream()
                            .map(item -> new ReservationItem(item.sku(), item.quantity()))
                            .toList()
            );
        } catch (ReservationDomainException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

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
