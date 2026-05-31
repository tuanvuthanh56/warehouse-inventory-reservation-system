package com.example.inventory.domain.factory;

import com.example.common.messaging.ReservationItemMessage;
import com.example.inventory.domain.exception.InventoryDomainException;
import com.example.inventory.domain.model.InventoryHoldStatus;
import com.example.inventory.infrastructure.persistence.InventoryHoldEntity;
import com.example.inventory.infrastructure.persistence.InventoryHoldItemEntity;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * Factory for creating inventory holds.
 *
 * It centralizes the rules for a valid hold: required reservation/order data,
 * valid items, duplicate SKU merging, initial HELD status, ids, and timestamps.
 */
public class InventoryHoldFactory {
    public InventoryHoldEntity createHeldHold(UUID reservationId, String orderId, List<ReservationItemMessage> requestedItems) {
        if (reservationId == null) {
            throw new InventoryDomainException("reservationId is required.");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new InventoryDomainException("orderId is required.");
        }
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new InventoryDomainException("Inventory hold must contain at least one item.");
        }

        Map<String, Integer> mergedItems = new LinkedHashMap<>();
        requestedItems.stream()
                .sorted(Comparator.comparing(item -> item == null ? "" : normalizedSku(item.sku())))
                .forEach(item -> {
                    if (item == null) {
                        throw new InventoryDomainException("item is required.");
                    }
                    String sku = normalizedSku(item.sku());
                    if (sku.isBlank()) {
                        throw new InventoryDomainException("sku is required.");
                    }
                    if (item.quantity() <= 0) {
                        throw new InventoryDomainException("quantity must be greater than zero.");
                    }
                    mergedItems.merge(sku, item.quantity(), Integer::sum);
                });

        Instant now = Instant.now();
        InventoryHoldEntity hold = new InventoryHoldEntity(
                UUID.randomUUID(),
                reservationId,
                orderId.trim(),
                InventoryHoldStatus.HELD,
                now
        );
        mergedItems.forEach((sku, quantity) ->
                hold.addItem(new InventoryHoldItemEntity(UUID.randomUUID(), sku, quantity, now))
        );
        return hold;
    }

    private String normalizedSku(String sku) {
        return sku == null ? "" : sku.trim();
    }
}
