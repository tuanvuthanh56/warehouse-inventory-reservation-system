package com.example.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReservationItemRequest(

        @Schema(example = "A100")
        @NotBlank String sku,

        @Schema(example = "5")
        @Min(1) int quantity
) {
}
