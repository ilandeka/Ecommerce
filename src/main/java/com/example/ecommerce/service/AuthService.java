package com.example.ecommerce.service;

import com.example.ecommerce.dto.AuthResponse;
import com.example.ecommerce.dto.LoginRequest;
import com.example.ecommerce.dto.RegisterRequest;
import com.example.ecommerce.exception.TokenRefreshException;
import com.example.ecommerce.model.RefreshToken;
import com.example.ecommerce.model.Role;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuthenticationManager authenticationManager, RefreshTokenService refreshTokenService, UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRoles(Collections.singleton(Role.ROLE_USER));
        user.setEnabled(true);

        User savedUser = userRepository.save(user);

        // Authenticate the user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Generate access token
        String accessToken = tokenProvider.generateToken(authentication);

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new AuthResponse(accessToken, refreshToken.getToken(), savedUser.getEmail(), savedUser.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate the user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Get the user details
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate access token
        String accessToken = tokenProvider.generateToken(authentication);

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Return the complete response
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getEmail(),
                user.getFullName()
        );
    }

    public String refreshToken(String refreshToken) {
        // Find and verify the refresh token
        RefreshToken refreshTokenEntity = refreshTokenService
                .findByToken(refreshToken)
                .orElseThrow(() -> new TokenRefreshException("Refresh token not found"));

        refreshTokenService.verifyExpiration(refreshTokenEntity);

        // Get the user's email
        String userEmail = refreshTokenEntity.getUser().getEmail();

        // Load the complete UserDetails object using UserDetailsService
        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

        // Create new authentication token with the UserDetails
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,      // principal (UserDetails object)
                null,            // credentials (null since we don't need password for refresh)
                userDetails.getAuthorities()  // authorities from UserDetails
        );

        // Generate new access token
        return tokenProvider.generateToken(authentication);
    }
}
