package com.example.inventory.api;

import com.example.inventory.api.dto.InventoryResponse;
import com.example.inventory.application.InventoryApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Read current stock by SKU")
public class InventoryController {
    private final InventoryApplicationService inventoryApplicationService;

    public InventoryController(InventoryApplicationService inventoryApplicationService) {
        this.inventoryApplicationService = inventoryApplicationService;
    }

    @GetMapping
    @Operation(summary = "List current stock for all SKUs")
    List<InventoryResponse> listStock() {
        return inventoryApplicationService.listStock();
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get current stock for a SKU")
    InventoryResponse getStock(@PathVariable String sku) {
        return inventoryApplicationService.getStock(sku);
    }
}
