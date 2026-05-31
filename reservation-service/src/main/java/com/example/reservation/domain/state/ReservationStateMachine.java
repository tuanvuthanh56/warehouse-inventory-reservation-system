package com.example.reservation.domain.state;

import com.example.reservation.domain.exception.ReservationDomainException;
import com.example.reservation.domain.model.ReservationStatus;

import java.util.Map;
import java.util.Set;

/**
 * Defines the allowed reservation status transitions for the async inventory workflow.
 */
public class ReservationStateMachine {

    /**
     * Terminal states intentionally have no outgoing transitions.
     */
    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReservationStatus.RESERVING, Set.of(ReservationStatus.PENDING, ReservationStatus.REJECTED, ReservationStatus.FAILED_RETRYABLE),
            ReservationStatus.PENDING, Set.of(ReservationStatus.CONFIRMING, ReservationStatus.CANCELLING),
            ReservationStatus.CONFIRMING, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.FAILED_RETRYABLE),
            ReservationStatus.CANCELLING, Set.of(ReservationStatus.CANCELLED, ReservationStatus.FAILED_RETRYABLE),
            ReservationStatus.CONFIRMED, Set.of(),
            ReservationStatus.CANCELLED, Set.of(),
            ReservationStatus.REJECTED, Set.of(),
            ReservationStatus.FAILED_RETRYABLE, Set.of(ReservationStatus.CONFIRMING, ReservationStatus.CANCELLING)
    );

    public void assertCanTransition(ReservationStatus current, ReservationStatus next) {
        if (!canTransition(current, next)) {
            throw new ReservationDomainException("Invalid reservation transition from " + current + " to " + next + ".");
        }
    }

    public boolean canTransition(ReservationStatus current, ReservationStatus next) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public ReservationStatus confirm(ReservationStatus current) {
        assertCanTransition(current, ReservationStatus.CONFIRMING);
        return ReservationStatus.CONFIRMING;
    }

    public ReservationStatus cancel(ReservationStatus current) {
        assertCanTransition(current, ReservationStatus.CANCELLING);
        return ReservationStatus.CANCELLING;
    }
}
