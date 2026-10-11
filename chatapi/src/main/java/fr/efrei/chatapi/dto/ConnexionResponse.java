package fr.efrei.chatapi.dto;

public record ConnexionResponse(String token, String type, Long id, String username) {
}
