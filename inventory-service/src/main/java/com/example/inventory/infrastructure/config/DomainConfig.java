package com.example.inventory.infrastructure.config;

import com.example.inventory.domain.factory.InventoryHoldFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {
    @Bean
    InventoryHoldFactory inventoryHoldFactory() {
        return new InventoryHoldFactory();
    }
}
