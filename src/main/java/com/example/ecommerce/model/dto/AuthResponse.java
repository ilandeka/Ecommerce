package com.example.ecommerce.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private String email;
    private String fullName;

    public AuthResponse(String accessToken, String refreshToken, String email, String fullName) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.email = email;
        this.fullName = fullName;
    }

}
