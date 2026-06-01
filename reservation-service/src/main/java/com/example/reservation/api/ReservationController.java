package com.example.reservation.api;

import com.example.common.api.ApiResponse;
import com.example.reservation.api.dto.CreateReservationRequest;
import com.example.reservation.api.dto.ReservationResponse;
import com.example.reservation.application.ReservationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Create, read, confirm, and cancel reservations")
public class ReservationController {
    private final ReservationApplicationService reservationApplicationService;

    public ReservationController(ReservationApplicationService reservationApplicationService) {
        this.reservationApplicationService = reservationApplicationService;
    }

    @PostMapping
    @Operation(summary = "Create a reservation", description = "Creates a reservation in RESERVING status and starts the async inventory hold flow.")
    ResponseEntity<ApiResponse<ReservationResponse>> create(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reservationApplicationService.create(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation")
    ApiResponse<ReservationResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(reservationApplicationService.get(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a pending reservation", description = "Moves PENDING to CONFIRMING and starts the async inventory confirm flow.")
    ResponseEntity<ApiResponse<ReservationResponse>> confirm(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(reservationApplicationService.confirm(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending reservation", description = "Moves PENDING to CANCELLING and starts the async inventory release flow.")
    ResponseEntity<ApiResponse<ReservationResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(reservationApplicationService.cancel(id)));
    }
}
