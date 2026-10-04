package fr.efrei.chatapi.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import fr.efrei.chatapi.dto.UserRequest;
import fr.efrei.chatapi.service.UserService;
import lombok.RequiredArgsConstructor;
import fr.efrei.chatapi.config.JwtUtil;
import fr.efrei.chatapi.repository.UtilisateurRepository;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor 
public class CompteController{
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final UtilisateurRepository utilisateurRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/auth/register")
    public ResponseEntity<?> createCompte(@RequestBody UserRequest userRequest) {
        // Implementation for creating a new user request
        if (utilisateurRepository.findByUsername(userRequest.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Username already exists !");
        }

        userRequest.setPassword(passwordEncoder.encode(userRequest.getPassword()));

        userService.createUser(userRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(userRequest);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody UserRequest userRequest) {
        // Implementation for user login
        try{
            Authentication authentification = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userRequest.getUsername(), userRequest.getPassword())
            );
            if(authentification.isAuthenticated()){
               Map<String, Object> authData = new HashMap<>();
               authData.put("username", userRequest.getUsername());
                authData.put("token", jwtUtil.generateToken(userRequest.getUsername()));
                authData.put("type", "Bearer");
                return ResponseEntity.ok(authData);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

    }

    @GetMapping ("/utilisateurs")
    public ResponseEntity<?> getAllUtilisateurs() {
        return userService.getAllUsers().isEmpty() ? ResponseEntity.status(HttpStatus.NO_CONTENT).body("No users found") : ResponseEntity.ok(userService.getAllUsers());
    }
}