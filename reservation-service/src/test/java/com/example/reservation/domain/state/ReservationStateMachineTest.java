package com.example.reservation.domain.state;

import com.example.reservation.domain.exception.ReservationDomainException;
import com.example.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationStateMachineTest {
    private final ReservationStateMachine stateMachine = new ReservationStateMachine();

    @Test
    void allowsValidTransitions() {
        stateMachine.assertCanTransition(ReservationStatus.RESERVING, ReservationStatus.PENDING);
        stateMachine.assertCanTransition(ReservationStatus.RESERVING, ReservationStatus.REJECTED);
        assertThat(stateMachine.confirm(ReservationStatus.PENDING)).isEqualTo(ReservationStatus.CONFIRMING);
        assertThat(stateMachine.cancel(ReservationStatus.PENDING)).isEqualTo(ReservationStatus.CANCELLING);
    }

    @Test
    void rejectsInvalidTransitions() {
        assertThatThrownBy(() -> stateMachine.cancel(ReservationStatus.CONFIRMED))
                .isInstanceOf(ReservationDomainException.class);
        assertThatThrownBy(() -> stateMachine.confirm(ReservationStatus.CANCELLED))
                .isInstanceOf(ReservationDomainException.class);
        assertThatThrownBy(() -> stateMachine.assertCanTransition(ReservationStatus.REJECTED, ReservationStatus.CONFIRMED))
                .isInstanceOf(ReservationDomainException.class);
    }
}
