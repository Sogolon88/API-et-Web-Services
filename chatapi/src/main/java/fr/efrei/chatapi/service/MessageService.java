package fr.efrei.chatapi.service;

import java.util.List;
import org.springframework.stereotype.Service;
import fr.efrei.chatapi.model.Message;
import fr.efrei.chatapi.repository.MessageRepository;

@Service 
public class MessageService {
    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    
    public List<Message> getAllMessages(Long currentUserId, Long utilisateurId) {
        return messageRepository.findMessages(currentUserId, utilisateurId);
    }

}
