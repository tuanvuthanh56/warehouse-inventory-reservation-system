package com.example.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateReservationRequest(
        @Schema(example = "ORD-1001")
        @NotBlank String orderId,
        @NotEmpty List<@Valid ReservationItemRequest> items
) {
}
