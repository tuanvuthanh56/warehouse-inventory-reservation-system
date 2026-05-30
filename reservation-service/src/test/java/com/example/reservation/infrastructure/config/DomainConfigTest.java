package com.example.reservation.infrastructure.config;

import com.example.reservation.domain.factory.ReservationFactory;
import com.example.reservation.domain.state.ReservationStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainConfigTest {
    private final DomainConfig config = new DomainConfig();

    @Test
    void exposesDomainServicesAsBeans() {
        assertThat(config.reservationFactory()).isInstanceOf(ReservationFactory.class);
        assertThat(config.reservationStateMachine()).isInstanceOf(ReservationStateMachine.class);
    }
}
