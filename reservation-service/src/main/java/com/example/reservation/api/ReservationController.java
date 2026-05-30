package com.example.reservation.api;

import com.example.reservation.api.dto.CreateReservationRequest;
import com.example.reservation.api.dto.ReservationResponse;
import com.example.reservation.application.ReservationApplicationService;
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
public class ReservationController {
    private final ReservationApplicationService reservationApplicationService;

    public ReservationController(ReservationApplicationService reservationApplicationService) {
        this.reservationApplicationService = reservationApplicationService;
    }

    @PostMapping
    ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reservationApplicationService.create(request));
    }

    @GetMapping("/{id}")
    ReservationResponse get(@PathVariable UUID id) {
        return reservationApplicationService.get(id);
    }

    @PostMapping("/{id}/confirm")
    ResponseEntity<ReservationResponse> confirm(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reservationApplicationService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reservationApplicationService.cancel(id));
    }
}
