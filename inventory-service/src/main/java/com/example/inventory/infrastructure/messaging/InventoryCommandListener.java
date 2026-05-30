package com.example.inventory.infrastructure.messaging;

import com.example.common.api.ErrorCode;
import com.example.common.messaging.ConfirmInventoryCommand;
import com.example.common.messaging.MessagingNames;
import com.example.common.messaging.ReleaseInventoryCommand;
import com.example.common.messaging.ReserveInventoryCommand;
import com.example.inventory.api.error.ApiException;
import com.example.inventory.application.InventoryApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandListener {
    private final InventoryApplicationService inventoryApplicationService;
    private final ObjectMapper objectMapper;

    public InventoryCommandListener(InventoryApplicationService inventoryApplicationService, ObjectMapper objectMapper) {
        this.inventoryApplicationService = inventoryApplicationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = MessagingNames.INVENTORY_COMMANDS_QUEUE)
    public void onMessage(String payload, @Header(MessagingNames.EVENT_TYPE_HEADER) String eventType) {
        try {
            if (ReserveInventoryCommand.class.getSimpleName().equals(eventType)) {
                inventoryApplicationService.reserve(objectMapper.readValue(payload, ReserveInventoryCommand.class));
            } else if (ConfirmInventoryCommand.class.getSimpleName().equals(eventType)) {
                inventoryApplicationService.confirm(objectMapper.readValue(payload, ConfirmInventoryCommand.class));
            } else if (ReleaseInventoryCommand.class.getSimpleName().equals(eventType)) {
                inventoryApplicationService.release(objectMapper.readValue(payload, ReleaseInventoryCommand.class));
            }
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.MESSAGE_PROCESSING_FAILED, "Unable to deserialize inventory command.", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
