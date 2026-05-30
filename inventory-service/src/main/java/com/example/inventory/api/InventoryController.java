package com.example.inventory.api;

import com.example.inventory.api.dto.InventoryResponse;
import com.example.inventory.application.InventoryApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryApplicationService inventoryApplicationService;

    public InventoryController(InventoryApplicationService inventoryApplicationService) {
        this.inventoryApplicationService = inventoryApplicationService;
    }

    @GetMapping("/{sku}")
    InventoryResponse getStock(@PathVariable String sku) {
        return inventoryApplicationService.getStock(sku);
    }
}
