package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class StompChatController {

    private final MessageService messageService;

    public StompChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send") // destino /app/chat.send
    public void receiveMessage(@Payload StompMessagePayload payload, Principal principal) {
        Long senderId = Long.valueOf(principal.getName()); // asume principal = userId
        messageService.sendAndStoreMessage(senderId, payload.getConversationId(),
                payload.getCiphertext(), payload.getEncryptedKeys());
    }
}
