package fr.efrei.chatapi.service;

import org.springframework.stereotype.Service;
import java.util.List;

import fr.efrei.chatapi.model.Utilisateur;
import fr.efrei.chatapi.dto.UserRequest;
import fr.efrei.chatapi.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class UserService {

    private final UtilisateurRepository utilisateurRepository;

    public void createUser(UserRequest userRequest) {

        Utilisateur utilisateur = new Utilisateur();

        utilisateur.setUsername(userRequest.getUsername());
        utilisateur.setPassword(userRequest.getPassword());

        utilisateurRepository.save(utilisateur);
    }

    public List<Utilisateur> getAllUsers() {
        return utilisateurRepository.findAll();
    }

    public boolean authenticateUser(UserRequest userRequest) {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(userRequest.getUsername());
        if (utilisateur != null && utilisateur.getPassword().equals(userRequest.getPassword())) {
            return true;
        }
        return false;
    }
    
}
