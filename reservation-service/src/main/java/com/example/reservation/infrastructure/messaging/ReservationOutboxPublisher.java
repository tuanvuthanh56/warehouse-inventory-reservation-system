package com.example.reservation.infrastructure.messaging;

import com.example.common.messaging.MessagingNames;
import com.example.reservation.infrastructure.persistence.OutboxEventEntity;
import com.example.reservation.infrastructure.persistence.OutboxEventRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes stored reservation commands to RabbitMQ after the business transaction commits.
 */
@Component
public class ReservationOutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public ReservationOutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEventEntity event : outboxEventRepository.findBatchForPublishing("NEW", 20)) {
            // Keep the serialized command type in a header so the listener can deserialize safely.
            rabbitTemplate.convertAndSend(
                    MessagingNames.INVENTORY_EXCHANGE,
                    MessagingNames.INVENTORY_COMMANDS_QUEUE,
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
