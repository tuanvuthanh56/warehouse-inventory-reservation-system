package com.example.common.messaging;

/**
 * RabbitMQ exchanges, queues, routing keys, and headers shared by both services.
 */
public final class MessagingNames {

    public static final String INVENTORY_EXCHANGE = "inventory.exchange";

    public static final String INVENTORY_COMMANDS_QUEUE = "inventory.commands";

    public static final String INVENTORY_EVENTS_QUEUE = "inventory.events";

    /**
     * Failed messages are routed here after listener retries are exhausted.
     */
    public static final String DEAD_LETTER_EXCHANGE = "inventory.dead-letter.exchange";
    public static final String INVENTORY_COMMANDS_DLQ = "inventory.commands.dlq";
    public static final String INVENTORY_EVENTS_DLQ = "inventory.events.dlq";

    public static final String EVENT_TYPE_HEADER = "eventType";

    private MessagingNames() {
    }
}
