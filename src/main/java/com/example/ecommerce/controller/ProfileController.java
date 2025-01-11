package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.ApiResponse;
import com.example.ecommerce.model.dto.ChangePasswordRequest;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(
                currentUser.getId(),
                request.getCurrentPassword(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );

        return ResponseEntity.ok(new ApiResponse(true, "Password changed successfully"));
    }
}