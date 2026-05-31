package com.example.inventory.infrastructure.config;

import com.example.common.messaging.MessagingNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology and listener retry policy for inventory-side consumers.
 */
@Configuration
public class MessagingConfig {
    @Bean
    DirectExchange inventoryExchange() {
        return new DirectExchange(MessagingNames.INVENTORY_EXCHANGE);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(MessagingNames.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue inventoryCommandsQueue() {
        return QueueBuilder.durable(MessagingNames.INVENTORY_COMMANDS_QUEUE)
                .deadLetterExchange(MessagingNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MessagingNames.INVENTORY_COMMANDS_DLQ)
                .build();
    }

    @Bean
    Queue inventoryEventsQueue() {
        return QueueBuilder.durable(MessagingNames.INVENTORY_EVENTS_QUEUE)
                .deadLetterExchange(MessagingNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MessagingNames.INVENTORY_EVENTS_DLQ)
                .build();
    }

    @Bean
    Queue inventoryCommandsDeadLetterQueue() {
        return QueueBuilder.durable(MessagingNames.INVENTORY_COMMANDS_DLQ).build();
    }

    @Bean
    Queue inventoryEventsDeadLetterQueue() {
        return QueueBuilder.durable(MessagingNames.INVENTORY_EVENTS_DLQ).build();
    }

    @Bean
    Binding inventoryCommandsBinding(Queue inventoryCommandsQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryCommandsQueue).to(inventoryExchange).with(MessagingNames.INVENTORY_COMMANDS_QUEUE);
    }

    @Bean
    Binding inventoryEventsBinding(Queue inventoryEventsQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryEventsQueue).to(inventoryExchange).with(MessagingNames.INVENTORY_EVENTS_QUEUE);
    }

    @Bean
    Binding inventoryCommandsDeadLetterBinding(Queue inventoryCommandsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(inventoryCommandsDeadLetterQueue).to(deadLetterExchange).with(MessagingNames.INVENTORY_COMMANDS_DLQ);
    }

    @Bean
    Binding inventoryEventsDeadLetterBinding(Queue inventoryEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(inventoryEventsDeadLetterQueue).to(deadLetterExchange).with(MessagingNames.INVENTORY_EVENTS_DLQ);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        // Exhausted listener retries are rejected so RabbitMQ routes the message to the DLQ.
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 30000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
