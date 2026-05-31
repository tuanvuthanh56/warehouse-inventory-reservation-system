package com.example.common.messaging;

/**
 * Describes the item-level inventory issue that caused a reservation rejection.
 */
public record UnavailableItemMessage(String sku, int requested, int available, String reason) {
}
