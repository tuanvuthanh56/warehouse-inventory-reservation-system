package com.example.reservation.application;

import com.example.common.messaging.InventoryConfirmedEvent;
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservationRejectedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.ReservationItemMessage;
import com.example.common.messaging.UnavailableItemMessage;
import com.example.reservation.api.dto.CreateReservationRequest;
import com.example.reservation.api.dto.ReservationItemRequest;
import com.example.reservation.api.error.ApiException;
import com.example.reservation.api.error.ConflictException;
import com.example.reservation.api.error.NotFoundException;
import com.example.reservation.domain.factory.ReservationFactory;
import com.example.reservation.domain.model.ReservationStatus;
import com.example.reservation.domain.state.ReservationStateMachine;
import com.example.reservation.infrastructure.persistence.InboxMessageRepository;
import com.example.reservation.infrastructure.persistence.OutboxEventEntity;
import com.example.reservation.infrastructure.persistence.OutboxEventRepository;
import com.example.reservation.infrastructure.persistence.ReservationEntity;
import com.example.reservation.infrastructure.persistence.ReservationItemEntity;
import com.example.reservation.infrastructure.persistence.ReservationRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationApplicationServiceTest {
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final InboxMessageRepository inboxMessageRepository = mock(InboxMessageRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ReservationApplicationService service = new ReservationApplicationService(
            new ReservationFactory(),
            new ReservationStateMachine(),
            reservationRepository,
            outboxEventRepository,
            inboxMessageRepository,
            objectMapper
    );

    @Test
    void createReturnsExistingReservationForSameOrderIdWithoutNewOutboxCommand() {
        var existing = reservation(UUID.randomUUID(), "ORD-1", ReservationStatus.PENDING);
        existing.addItem(new ReservationItemEntity(UUID.randomUUID(), "A100", 2, Instant.now()));
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(existing));

        var response = service.create(createRequest(" ORD-1 ", new ReservationItemRequest("A100", 2)));

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.items()).hasSize(1);
        verify(reservationRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void createPersistsNewReservationAndReserveInventoryCommand() throws Exception {
        when(reservationRepository.findByOrderId("ORD-2")).thenReturn(Optional.empty());

        var response = service.create(createRequest("ORD-2",
                new ReservationItemRequest("A100", 2),
                new ReservationItemRequest("A100", 3),
                new ReservationItemRequest("B200", 1)
        ));

        assertThat(response.orderId()).isEqualTo("ORD-2");
        assertThat(response.status()).isEqualTo("RESERVING");
        assertThat(response.items()).extracting("sku").containsExactly("A100", "B200");
        assertThat(response.items()).extracting("quantity").containsExactly(5, 1);

        ArgumentCaptor<ReservationEntity> reservationCaptor = ArgumentCaptor.forClass(ReservationEntity.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getItems()).hasSize(2);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("ReserveInventoryCommand");
        assertThat(objectMapper.readTree(outboxCaptor.getValue().getPayload()).get("orderId").asText()).isEqualTo("ORD-2");
    }

    @Test
    void createWrapsDomainValidationErrorsAsBadRequestApiException() {
        when(reservationRepository.findByOrderId("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest("", new ReservationItemRequest("A100", 1))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("orderId is required");
    }

    @Test
    void getReturnsReservationResponse() {
        UUID id = UUID.randomUUID();
        var reservation = reservation(id, "ORD-1", ReservationStatus.PENDING);
        reservation.addItem(new ReservationItemEntity(UUID.randomUUID(), "A100", 2, Instant.now()));
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        var response = service.get(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.orderId()).isEqualTo("ORD-1");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getThrowsNotFoundForUnknownReservation() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Reservation not found.");
    }

    @Test
    void confirmPendingReservationMovesToConfirmingAndSavesCommand() {
        UUID id = UUID.randomUUID();
        var reservation = reservation(id, "ORD-1", ReservationStatus.PENDING);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        var response = service.confirm(id);

        assertThat(response.status()).isEqualTo("CONFIRMING");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMING);
        assertOutboxEventType("ConfirmInventoryCommand");
    }

    @Test
    void confirmRejectsNonPendingReservation() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation(id, "ORD-1", ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> service.confirm(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only PENDING reservations can be confirmed.");
    }

    @Test
    void cancelPendingReservationMovesToCancellingAndSavesCommand() {
        UUID id = UUID.randomUUID();
        var reservation = reservation(id, "ORD-1", ReservationStatus.PENDING);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        var response = service.cancel(id);

        assertThat(response.status()).isEqualTo("CANCELLING");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLING);
        assertOutboxEventType("ReleaseInventoryCommand");
    }

    @Test
    void cancelRejectsNonPendingReservation() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation(id, "ORD-1", ReservationStatus.REJECTED)));

        assertThatThrownBy(() -> service.cancel(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only PENDING reservations can be cancelled.");
    }

    @Test
    void handleInventoryReservedMovesReservingReservationToPending() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.RESERVING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryReserved(new InventoryReservedEvent(
                messageId,
                reservationId,
                "ORD-1",
                UUID.randomUUID(),
                List.of(new ReservationItemMessage("A100", 1)),
                Instant.now()
        ));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void handleInventoryRejectedMovesReservingReservationToRejectedWithReason() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.RESERVING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryRejected(new InventoryReservationRejectedEvent(
                messageId,
                reservationId,
                "ORD-1",
                "INSUFFICIENT_STOCK",
                List.of(new UnavailableItemMessage("A100", 3, 1)),
                Instant.now()
        ));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getFailureReason()).isEqualTo("INSUFFICIENT_STOCK");
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void handleInventoryConfirmedMovesConfirmingReservationToConfirmed() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.CONFIRMING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryConfirmed(new InventoryConfirmedEvent(messageId, reservationId, "ORD-1", Instant.now()));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void handleInventoryReleasedMovesCancellingReservationToCancelled() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.CANCELLING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryReleased(new InventoryReleasedEvent(messageId, reservationId, "ORD-1", Instant.now()));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void duplicateInventoryEventIsIgnored() {
        UUID messageId = UUID.randomUUID();
        when(inboxMessageRepository.existsById(messageId)).thenReturn(true);

        service.handleInventoryConfirmed(new InventoryConfirmedEvent(messageId, UUID.randomUUID(), "ORD-1", Instant.now()));

        verify(reservationRepository, never()).findById(any());
        verify(inboxMessageRepository, never()).save(any());
    }

    @Test
    void lateInventoryEventDoesNotChangeUnexpectedStateButIsRecorded() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.CANCELLED);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryConfirmed(new InventoryConfirmedEvent(messageId, reservationId, "ORD-1", Instant.now()));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void lateInventoryReservedEventDoesNotChangeNonReservingReservationButIsRecorded() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.PENDING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryReserved(new InventoryReservedEvent(
                messageId,
                reservationId,
                "ORD-1",
                UUID.randomUUID(),
                List.of(new ReservationItemMessage("A100", 1)),
                Instant.now()
        ));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void lateInventoryRejectedEventDoesNotChangeNonReservingReservationButIsRecorded() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.PENDING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryRejected(new InventoryReservationRejectedEvent(
                messageId,
                reservationId,
                "ORD-1",
                "INSUFFICIENT_STOCK",
                List.of(new UnavailableItemMessage("A100", 3, 1)),
                Instant.now()
        ));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getFailureReason()).isNull();
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void lateInventoryReleasedEventDoesNotChangeNonCancellingReservationButIsRecorded() {
        UUID reservationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var reservation = reservation(reservationId, "ORD-1", ReservationStatus.PENDING);
        when(inboxMessageRepository.existsById(messageId)).thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        service.handleInventoryReleased(new InventoryReleasedEvent(messageId, reservationId, "ORD-1", Instant.now()));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void writeJsonFailureIsWrappedAsApiException() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        var failingService = new ReservationApplicationService(
                new ReservationFactory(),
                new ReservationStateMachine(),
                reservationRepository,
                outboxEventRepository,
                inboxMessageRepository,
                failingMapper
        );
        when(reservationRepository.findByOrderId("ORD-FAIL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> failingService.create(createRequest("ORD-FAIL", new ReservationItemRequest("A100", 1))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Unable to serialize message payload.");
    }

    private CreateReservationRequest createRequest(String orderId, ReservationItemRequest... items) {
        return new CreateReservationRequest(orderId, List.of(items));
    }

    private ReservationEntity reservation(UUID id, String orderId, ReservationStatus status) {
        Instant now = Instant.now();
        return new ReservationEntity(id, orderId, status, null, now, now);
    }

    private void assertOutboxEventType(String eventType) {
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(eventType);
    }
}
