package fr.efrei.chatapi.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.*;


public class UserRequest {
    @NotBlank(message = "Username cannot be empty")
    @Column(unique = true)
    private String username;

    @NotBlank(message = "Password cannot be empty")
    private String password;

    // Getters and setters
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

}
