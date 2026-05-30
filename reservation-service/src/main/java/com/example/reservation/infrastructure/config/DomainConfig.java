package com.example.reservation.infrastructure.config;

import com.example.reservation.domain.factory.ReservationFactory;
import com.example.reservation.domain.state.ReservationStateMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {
    @Bean
    ReservationFactory reservationFactory() {
        return new ReservationFactory();
    }

    @Bean
    ReservationStateMachine reservationStateMachine() {
        return new ReservationStateMachine();
    }
}
