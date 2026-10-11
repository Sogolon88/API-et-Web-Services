package fr.efrei.chatapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps JSON de l'inscription et du login. La borne de 72 caractères correspond
 * à la limite de BCrypt, au-delà de laquelle le hachage est refusé.
 */
public record IdentifiantsRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 6, max = 72) String password) {
}
