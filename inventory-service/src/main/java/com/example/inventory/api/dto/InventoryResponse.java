package com.example.inventory.api.dto;

import java.time.Instant;

public record InventoryResponse(
        String sku,
        int onHandStock,
        int availableStock,
        int reservedStock,
        Instant updatedAt
) {
}
