package com.example.ecommerce.service;

import com.example.ecommerce.exception.AuthenticationException;
import com.example.ecommerce.exception.TokenRefreshException;
import com.example.ecommerce.exception.UserAlreadyExistsException;
import com.example.ecommerce.model.dto.AdminRegisterRequest;
import com.example.ecommerce.model.dto.AuthResponse;
import com.example.ecommerce.model.dto.LoginRequest;
import com.example.ecommerce.model.dto.RegisterRequest;
import com.example.ecommerce.model.entity.RefreshToken;
import com.example.ecommerce.model.entity.Role;
import com.example.ecommerce.model.entity.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtTokenProvider;
import com.example.ecommerce.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
@Transactional
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.admin.registration-code}")
    private String adminRegistrationCode;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
    }

    // Regular user registration
    public AuthResponse register(RegisterRequest request) {
        // Check if email is already taken
        validateNewUser(request.getEmail());

        // Create user with ROLE_USER
        User user = createUser(request, Collections.singleton(Role.ROLE_USER));

        // Authenticate and generate tokens
        return authenticateAndGenerateResponse(request.getEmail(),
                request.getPassword(),
                user);
    }

    // Admin registration
    public AuthResponse registerAdmin(AdminRegisterRequest request) {
        // Verify admin registration code
        validateAdminCode(request.getAdminCode());

        // Check if email is already taken
        validateNewUser(request.getEmail());

        // Create user with both ROLE_ADMIN and ROLE_USER
        User user = createUser(request,
                new HashSet<>(Arrays.asList(Role.ROLE_ADMIN, Role.ROLE_USER)));

        // Authenticate and generate tokens
        return authenticateAndGenerateResponse(request.getEmail(),
                request.getPassword(),
                user);
    }

    // Helper method to validate admin registration code
    private void validateAdminCode(String providedCode) {
        if (!adminRegistrationCode.equals(providedCode)) {
            throw new RuntimeException("Invalid admin registration code");
        }
    }

    // Helper method to check if email is already registered
    private void validateNewUser(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Email already registered");
        }
    }

    // Helper method to create a new user
    private User createUser(RegisterRequest request, Set<Role> roles) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRoles(roles);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    // Helper method to authenticate user and generate tokens
    private AuthResponse authenticateAndGenerateResponse(String email, String password, User user) {
        // Create authentication token with credentials
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        // Generate JWT access token
        String accessToken = tokenProvider.generateToken(authentication);

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService
                .createRefreshToken(user.getId());

        // Return complete authentication response
        return new AuthResponse(accessToken,
                refreshToken.getToken(),
                user.getEmail(),
                user.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            // First, verify that the user exists
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

            // Attempt authentication with the provided credentials
            Authentication authentication = authenticateUser(
                    request.getEmail(),
                    request.getPassword()
            );

            // If authentication succeeded, generate the tokens and response
            return generateAuthResponse(authentication, user);

        } catch (AuthenticationException e) {
            // Log the failed attempt but don't expose specific failure reasons
            logger.warn("Failed login attempt for user: {}", request.getEmail());
            throw new AuthenticationException("Invalid credentials");
        }
    }

    public String refreshToken(String refreshToken) {
        try {
            // Find and verify the refresh token exists and is valid
            RefreshToken refreshTokenEntity = validateRefreshToken(refreshToken);

            // Get user details and create a new authentication
            Authentication authentication = createAuthenticationFromRefreshToken(
                    refreshTokenEntity.getUser()
            );

            // Generate and return new access token
            return tokenProvider.generateToken(authentication);

        } catch (Exception e) {
            logger.error("Error during token refresh", e);
            throw new TokenRefreshException("Failed to refresh token: " + e.getMessage());
        }
    }

    // Helper method for user authentication
    private Authentication authenticateUser(String email, String password) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException e) {
            logger.warn("Authentication failed for user: {}", email);
            throw new AuthenticationException("Invalid credentials");
        }
    }

    // Helper method to generate authentication response
    private AuthResponse generateAuthResponse(Authentication authentication, User user) {
        // Generate access token
        String accessToken = tokenProvider.generateToken(authentication);

        // Generate new refresh token, deleting any existing ones
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Update last login timestamp
        updateLastLogin(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getEmail(),
                user.getFullName()
        );
    }

    // Helper method to validate refresh token
    private RefreshToken validateRefreshToken(String token) {
        return refreshTokenService.findByToken(token)
                .map(refreshTokenService::verifyExpiration)
                .orElseThrow(() -> new TokenRefreshException("Refresh token not found"));
    }

    // Helper method to create authentication from refresh token
    private Authentication createAuthenticationFromRefreshToken(User user) {
        UserDetails userDetails = UserPrincipal.create(user);

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    // Helper method to update last login timestamp
    private void updateLastLogin(User user) {
        user.setLastLogin(Instant.now());
        userRepository.save(user);
    }
}