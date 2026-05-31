package com.example.common.messaging;

/**
 * SKU and quantity pair used in reservation-related messages.
 */
public record ReservationItemMessage(String sku, int quantity) {
}
