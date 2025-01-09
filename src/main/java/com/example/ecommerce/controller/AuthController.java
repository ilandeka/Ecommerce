package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.*;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.AuthService;
import com.example.ecommerce.service.RefreshTokenService;
import com.example.ecommerce.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, TokenBlacklistService tokenBlacklistService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(new AuthResponse(
                null,
                null,
                userPrincipal.getUsername(),
                userPrincipal.getFullName()
        ));
    }

    @PostMapping("/register/admin")
    public ResponseEntity<AuthResponse> registerAdmin(@Valid @RequestBody AdminRegisterRequest request) {
        return ResponseEntity.ok(authService.registerAdmin(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        // Get new access token
        String accessToken = authService.refreshToken(request.getRefreshToken());

        // Create response with new access token and same refresh token
        return ResponseEntity.ok(new TokenRefreshResponse(
                accessToken,
                request.getRefreshToken()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request, @RequestHeader(value = "Authorization", required = true) String authHeader) {
        // If we reach this point, it means the token is valid (thanks to Spring Security)
        try {
            if (!authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid authorization header format"));
            }

            String jwt = authHeader.substring(7);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
                UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

                // Delete refresh token
                refreshTokenService.deleteByUserId(userPrincipal.getId());

                // Blacklist the current access token
                tokenBlacklistService.blacklistToken(jwt);

                // Clear security context
                SecurityContextHolder.clearContext();

                return ResponseEntity.ok(new ApiResponse(true, "Logged out successfully"));
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Invalid authentication"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error during logout: " + e.getMessage()));
        }
    }
}