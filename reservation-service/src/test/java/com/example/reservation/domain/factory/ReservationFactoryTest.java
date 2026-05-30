package com.example.reservation.domain.factory;

import com.example.reservation.domain.exception.ReservationDomainException;
import com.example.reservation.domain.model.ReservationItem;
import com.example.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationFactoryTest {
    private final ReservationFactory factory = new ReservationFactory();

    @Test
    void createsReservingReservationAndMergesDuplicateSkus() {
        var reservation = factory.create(" ORD-1001 ", List.of(
                new ReservationItem("A100", 2),
                new ReservationItem("A100", 3),
                new ReservationItem("B200", 1)
        ));

        assertThat(reservation.orderId()).isEqualTo("ORD-1001");
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVING);
        assertThat(reservation.items()).containsExactly(
                new ReservationItem("A100", 5),
                new ReservationItem("B200", 1)
        );
    }

    @Test
    void rejectsInvalidReservation() {
        assertThatThrownBy(() -> factory.create("", List.of(new ReservationItem("A100", 1))))
                .isInstanceOf(ReservationDomainException.class);
        assertThatThrownBy(() -> factory.create("ORD-1", List.of()))
                .isInstanceOf(ReservationDomainException.class);
        assertThatThrownBy(() -> factory.create("ORD-1", List.of(new ReservationItem("A100", 0))))
                .isInstanceOf(ReservationDomainException.class);
    }
}
