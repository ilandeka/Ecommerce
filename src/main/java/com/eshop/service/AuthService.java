package com.eshop.service;

import com.eshop.exception.AuthenticationException;
import com.eshop.exception.TokenRefreshException;
import com.eshop.exception.UserAlreadyExistsException;
import com.eshop.model.dto.AdminRegisterRequest;
import com.eshop.model.dto.AuthResponse;
import com.eshop.model.dto.LoginRequest;
import com.eshop.model.dto.RegisterRequest;
import com.eshop.model.entity.RefreshToken;
import com.eshop.model.entity.Role;
import com.eshop.model.entity.User;
import com.eshop.repository.UserRepository;
import com.eshop.security.JwtTokenProvider;
import com.eshop.security.UserPrincipal;
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
import java.util.stream.Collectors;

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
                user,
                false);
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
                user,
                false);
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
    private AuthResponse authenticateAndGenerateResponse(String email, String password, User user, boolean rememberMe) {
        // Create authentication token with credentials
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        // Generate JWT access token
        String accessToken = tokenProvider.generateToken(authentication);

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                user.getId(),
                rememberMe
        );

        // Convert roles from the User entity to a Set of strings
        Set<String> roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toSet());

        // Return complete authentication response with roles
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getEmail(),
                user.getFullName(),
                roles
        );
    }

    public AuthResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new AuthenticationException("User not found"));

            // Verify user exists and credentials are valid
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Generate tokens with remember me preference
            String accessToken = tokenProvider.generateToken(authentication);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                    user.getId(),
                    request.isRememberMe()
            );

            // Update last login
            user.setLastLogin(Instant.now());
            userRepository.save(user);

            // Convert roles properly using stream
            Set<String> roles = user.getRoles().stream()
                    .map(Role::name)
                    .collect(Collectors.toSet());

            return new AuthResponse(
                    accessToken,
                    refreshToken.getToken(),
                    user.getEmail(),
                    user.getFullName(),
                    roles
            );
        } catch (Exception e) {
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
}