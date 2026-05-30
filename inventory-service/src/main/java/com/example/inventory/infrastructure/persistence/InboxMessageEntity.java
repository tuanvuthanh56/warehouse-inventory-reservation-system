package com.example.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_messages")
public class InboxMessageEntity {
    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected InboxMessageEntity() {
    }

    public InboxMessageEntity(UUID messageId, String messageType) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.processedAt = Instant.now();
    }
}
