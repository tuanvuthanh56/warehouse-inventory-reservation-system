package com.example.inventory.infrastructure.messaging;

import com.example.common.messaging.MessagingNames;
import com.example.inventory.infrastructure.persistence.OutboxEventEntity;
import com.example.inventory.infrastructure.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryOutboxPublisherTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final InventoryOutboxPublisher publisher = new InventoryOutboxPublisher(repository, rabbitTemplate);

    @Test
    void publishesPendingEventsWithEventTypeHeader() {
        var event = new OutboxEventEntity(UUID.randomUUID(), "InventoryReservedEvent", "{\"ok\":true}");
        when(repository.findBatchForPublishing("NEW", 20)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            MessagePostProcessor postProcessor = invocation.getArgument(3);
            Message processed = postProcessor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertThat((Object) processed.getMessageProperties().getHeader(MessagingNames.EVENT_TYPE_HEADER))
                    .isEqualTo("InventoryReservedEvent");
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(MessagingNames.INVENTORY_EXCHANGE),
                eq(MessagingNames.INVENTORY_EVENTS_QUEUE),
                eq("{\"ok\":true}"),
                any(MessagePostProcessor.class)
        );

        publisher.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(MessagingNames.INVENTORY_EXCHANGE),
                eq(MessagingNames.INVENTORY_EVENTS_QUEUE),
                eq("{\"ok\":true}"),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    void doesNothingWhenNoEventsArePending() {
        when(repository.findBatchForPublishing("NEW", 20)).thenReturn(List.of());

        publisher.publishPendingEvents();

        verify(repository).findBatchForPublishing("NEW", 20);
    }
}
