package fr.efrei.chatapi.security;

import java.util.List;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import fr.efrei.chatapi.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;

/**
 * Utilisé uniquement au login : l'AuthenticationManager y charge le hash
 * pour le comparer au mot de passe saisi via BCrypt.
 */
@Service
@RequiredArgsConstructor
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return utilisateurRepository.findByUsername(username)
                .map(u -> new User(u.getUsername(), u.getPassword(), List.of()))
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur inconnu"));
    }
}
