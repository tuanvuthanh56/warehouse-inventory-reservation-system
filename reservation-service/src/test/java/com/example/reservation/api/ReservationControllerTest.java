package com.example.reservation.api;

import com.example.reservation.api.dto.CreateReservationRequest;
import com.example.reservation.api.dto.ReservationItemRequest;
import com.example.reservation.api.dto.ReservationResponse;
import com.example.reservation.application.ReservationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationControllerTest {
    private final ReservationApplicationService service = mock(ReservationApplicationService.class);
    private final ReservationController controller = new ReservationController(service);

    @Test
    void createReturnsAcceptedReservation() {
        var request = new CreateReservationRequest("ORD-1", List.of(new ReservationItemRequest("A100", 1)));
        var response = response("RESERVING");
        when(service.create(request)).thenReturn(response);

        var entity = controller.create(request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody().data()).isEqualTo(response);
        assertThat(entity.getBody().error()).isNull();
    }

    @Test
    void getDelegatesToApplicationService() {
        UUID id = UUID.randomUUID();
        var response = response("PENDING");
        when(service.get(id)).thenReturn(response);

        var entity = controller.get(id);

        assertThat(entity.data()).isEqualTo(response);
        assertThat(entity.error()).isNull();
    }

    @Test
    void confirmReturnsAcceptedReservation() {
        UUID id = UUID.randomUUID();
        var response = response("CONFIRMING");
        when(service.confirm(id)).thenReturn(response);

        var entity = controller.confirm(id);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(entity.getBody().data()).isEqualTo(response);
        assertThat(entity.getBody().error()).isNull();
        verify(service).confirm(id);
    }

    @Test
    void cancelReturnsAcceptedReservation() {
        UUID id = UUID.randomUUID();
        var response = response("CANCELLING");
        when(service.cancel(id)).thenReturn(response);

        var entity = controller.cancel(id);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(entity.getBody().data()).isEqualTo(response);
        assertThat(entity.getBody().error()).isNull();
        verify(service).cancel(id);
    }

    private ReservationResponse response(String status) {
        return new ReservationResponse(UUID.randomUUID(), "ORD-1", status, null, List.of(), Instant.now(), Instant.now());
    }
}
