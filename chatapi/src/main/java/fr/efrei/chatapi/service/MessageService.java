package fr.efrei.chatapi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import fr.efrei.chatapi.dto.MessageDto;
import fr.efrei.chatapi.repository.MessageRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    public List<MessageDto> historique(Long utilisateurConnecteId, Long interlocuteurId) {
        return messageRepository.findMessages(utilisateurConnecteId, interlocuteurId).stream()
                .map(MessageDto::depuis)
                .toList();
    }
}
