package com.eshop.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfile {
    private String email;
    private String fullName;

    public UserProfile(String email, String fullName) {
        this.email = email;
        this.fullName = fullName;
    }
}