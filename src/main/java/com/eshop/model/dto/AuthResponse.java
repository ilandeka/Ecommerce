package com.eshop.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter @Setter
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String fullName;
    private Set<String> roles;  // Adding roles field

    public AuthResponse(String accessToken, String refreshToken, String email, String fullName, Set<String> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles;
    }
}
