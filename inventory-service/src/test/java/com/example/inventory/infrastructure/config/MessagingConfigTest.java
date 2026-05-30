package com.example.inventory.infrastructure.config;

import com.example.common.messaging.MessagingNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingConfigTest {
    private final MessagingConfig config = new MessagingConfig();

    @Test
    void declaresInventoryExchangeAndQueues() {
        assertThat(config.inventoryExchange().getName()).isEqualTo(MessagingNames.INVENTORY_EXCHANGE);
        assertThat(config.deadLetterExchange().getName()).isEqualTo(MessagingNames.DEAD_LETTER_EXCHANGE);
        assertThat(config.inventoryCommandsQueue().getName()).isEqualTo(MessagingNames.INVENTORY_COMMANDS_QUEUE);
        assertThat(config.inventoryEventsQueue().getName()).isEqualTo(MessagingNames.INVENTORY_EVENTS_QUEUE);
        assertThat(config.inventoryCommandsDeadLetterQueue().getName()).isEqualTo(MessagingNames.INVENTORY_COMMANDS_DLQ);
        assertThat(config.inventoryEventsDeadLetterQueue().getName()).isEqualTo(MessagingNames.INVENTORY_EVENTS_DLQ);
    }

    @Test
    void declaresBindingsWithExpectedRoutingKeys() {
        var exchange = config.inventoryExchange();
        var deadLetterExchange = config.deadLetterExchange();

        assertThat(config.inventoryCommandsBinding(config.inventoryCommandsQueue(), exchange).getRoutingKey())
                .isEqualTo(MessagingNames.INVENTORY_COMMANDS_QUEUE);
        assertThat(config.inventoryEventsBinding(config.inventoryEventsQueue(), exchange).getRoutingKey())
                .isEqualTo(MessagingNames.INVENTORY_EVENTS_QUEUE);
        assertThat(config.inventoryCommandsDeadLetterBinding(config.inventoryCommandsDeadLetterQueue(), deadLetterExchange).getRoutingKey())
                .isEqualTo(MessagingNames.INVENTORY_COMMANDS_DLQ);
        assertThat(config.inventoryEventsDeadLetterBinding(config.inventoryEventsDeadLetterQueue(), deadLetterExchange).getRoutingKey())
                .isEqualTo(MessagingNames.INVENTORY_EVENTS_DLQ);
    }

    @Test
    void createsRabbitListenerContainerFactory() {
        assertThat(config.rabbitListenerContainerFactory(mock(ConnectionFactory.class))).isNotNull();
    }
}
