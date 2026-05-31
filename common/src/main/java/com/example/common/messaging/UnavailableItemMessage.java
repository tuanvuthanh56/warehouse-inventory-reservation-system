package com.example.common.messaging;

/**
 * Describes the shortage that caused an inventory reservation rejection.
 */
public record UnavailableItemMessage(String sku, int requested, int available) {
}
