package com.example.common.messaging;

public final class MessagingNames {
    public static final String INVENTORY_EXCHANGE = "inventory.exchange";
    public static final String INVENTORY_COMMANDS_QUEUE = "inventory.commands";
    public static final String INVENTORY_EVENTS_QUEUE = "inventory.events";
    public static final String EVENT_TYPE_HEADER = "eventType";

    private MessagingNames() {
    }
}
