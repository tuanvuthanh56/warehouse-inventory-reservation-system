package com.example.inventory.infrastructure.messaging;

import com.example.common.messaging.MessagingNames;
import com.example.inventory.infrastructure.persistence.OutboxEventEntity;
import com.example.inventory.infrastructure.persistence.OutboxEventRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes stored inventory events to RabbitMQ after the business transaction commits.
 */
@Component
public class InventoryOutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public InventoryOutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEventEntity event : outboxEventRepository.findBatchForPublishing("NEW", 20)) {
            // Keep the serialized event type in a header so the listener can deserialize safely.
            rabbitTemplate.convertAndSend(
                    MessagingNames.INVENTORY_EXCHANGE,
                    MessagingNames.INVENTORY_EVENTS_QUEUE,
                    event.getPayload(),
                    message -> {
                        message.getMessageProperties().setHeader(MessagingNames.EVENT_TYPE_HEADER, event.getEventType());
                        return message;
                    }
            );

            event.markPublished();
        }
    }
}
