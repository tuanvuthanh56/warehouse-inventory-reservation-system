package com.example.reservation.api.dto;

public record ReservationItemResponse(String sku, int quantity, Integer availableStock, String failureReason) {
}
