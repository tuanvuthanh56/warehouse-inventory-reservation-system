package com.example.reservation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * Abstraction for reservation domain objects.
 *
 * The factory returns this interface so application code depends on the
 * reservation contract, not a concrete reservation implementation.
 */
public interface Reservation {
    UUID id();

    String orderId();

    ReservationStatus status();

    String failureReason();

    List<ReservationItem> items();

    Instant createdAt();

    Instant updatedAt();
}
