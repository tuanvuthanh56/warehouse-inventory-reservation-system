package com.example.reservation.infrastructure.messaging;

import com.example.common.messaging.MessagingNames;
import com.example.reservation.infrastructure.persistence.OutboxEventEntity;
import com.example.reservation.infrastructure.persistence.OutboxEventRepository;
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

class ReservationOutboxPublisherTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ReservationOutboxPublisher publisher = new ReservationOutboxPublisher(repository, rabbitTemplate);

    @Test
    void publishesPendingCommandsWithEventTypeHeader() {
        var event = new OutboxEventEntity(UUID.randomUUID(), "ReserveInventoryCommand", "{\"ok\":true}");
        when(repository.findBatchForPublishing("NEW", 20)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            MessagePostProcessor postProcessor = invocation.getArgument(3);
            Message processed = postProcessor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertThat((Object) processed.getMessageProperties().getHeader(MessagingNames.EVENT_TYPE_HEADER))
                    .isEqualTo("ReserveInventoryCommand");
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(MessagingNames.INVENTORY_EXCHANGE),
                eq(MessagingNames.INVENTORY_COMMANDS_QUEUE),
                eq("{\"ok\":true}"),
                any(MessagePostProcessor.class)
        );

        publisher.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(MessagingNames.INVENTORY_EXCHANGE),
                eq(MessagingNames.INVENTORY_COMMANDS_QUEUE),
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
