package fr.efrei.chatapi.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import fr.efrei.chatapi.config.JwtUtil;
import fr.efrei.chatapi.repository.UtilisateurRepository;

import lombok.RequiredArgsConstructor;

@RequestMapping("/api/auth")
@RestController 
@RequiredArgsConstructor 
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UtilisateurRepository utilisateurRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
}
