package com.example.common.messaging;

public record UnavailableItemMessage(String sku, int requested, int available) {
}
