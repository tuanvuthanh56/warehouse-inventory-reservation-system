package com.example.inventory.infrastructure.config;

import com.example.common.messaging.MessagingNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {
    @Bean
    DirectExchange inventoryExchange() {
        return new DirectExchange(MessagingNames.INVENTORY_EXCHANGE);
    }

    @Bean
    Queue inventoryCommandsQueue() {
        return new Queue(MessagingNames.INVENTORY_COMMANDS_QUEUE, true);
    }

    @Bean
    Queue inventoryEventsQueue() {
        return new Queue(MessagingNames.INVENTORY_EVENTS_QUEUE, true);
    }

    @Bean
    Binding inventoryCommandsBinding(Queue inventoryCommandsQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryCommandsQueue).to(inventoryExchange).with(MessagingNames.INVENTORY_COMMANDS_QUEUE);
    }

    @Bean
    Binding inventoryEventsBinding(Queue inventoryEventsQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryEventsQueue).to(inventoryExchange).with(MessagingNames.INVENTORY_EVENTS_QUEUE);
    }
}
