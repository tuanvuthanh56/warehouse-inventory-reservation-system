package com.example.reservation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReservationItemRequest(
        @NotBlank String sku,
        @Min(1) int quantity
) {
}
