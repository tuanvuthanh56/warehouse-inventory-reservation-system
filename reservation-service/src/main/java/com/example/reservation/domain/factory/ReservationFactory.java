package com.example.reservation.domain.factory;

import com.example.reservation.domain.exception.ReservationDomainException;
import com.example.reservation.domain.model.Reservation;
import com.example.reservation.domain.model.ReservationItem;
import com.example.reservation.domain.model.ReservationStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReservationFactory {
    public Reservation create(String orderId, List<ReservationItem> requestedItems) {
        if (orderId == null || orderId.isBlank()) {
            throw new ReservationDomainException("orderId is required.");
        }
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new ReservationDomainException("Reservation must contain at least one item.");
        }

        Map<String, Integer> mergedItems = new LinkedHashMap<>();
        for (ReservationItem item : requestedItems) {
            if (item.sku() == null || item.sku().isBlank()) {
                throw new ReservationDomainException("sku is required.");
            }
            if (item.quantity() <= 0) {
                throw new ReservationDomainException("quantity must be greater than zero.");
            }
            mergedItems.merge(item.sku().trim(), item.quantity(), Integer::sum);
        }

        Instant now = Instant.now();
        return new Reservation(
                UUID.randomUUID(),
                orderId.trim(),
                ReservationStatus.RESERVING,
                null,
                mergedItems.entrySet().stream()
                        .map(entry -> new ReservationItem(entry.getKey(), entry.getValue()))
                        .toList(),
                now,
                now
        );
    }
}
