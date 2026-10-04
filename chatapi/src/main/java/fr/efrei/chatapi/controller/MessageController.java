package fr.efrei.chatapi.controller;

import org.springframework.web.bind.annotation.*;
import java.util.List;

import fr.efrei.chatapi.service.MessageService;
import fr.efrei.chatapi.model.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import fr.efrei.chatapi.model.Utilisateur;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping ("/{userId}")
    public ResponseEntity<List<Message>> getAllMessages(@PathVariable Long userId, Authentication authentication) {
        // Implementation for retrieving all messages for a specific user
        Utilisateur user = (Utilisateur) authentication.getPrincipal(); 
        List<Message> messages = messageService.getAllMessages( user.getId(), userId);
        return ResponseEntity.ok(messages);
    }
    
}
