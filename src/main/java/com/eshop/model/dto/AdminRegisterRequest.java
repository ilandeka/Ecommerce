package com.eshop.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AdminRegisterRequest extends RegisterRequest {
    private String adminCode; // This will be used to verify admin registration
}