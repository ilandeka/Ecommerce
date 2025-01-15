package com.example.ecommerce.service;

import com.example.ecommerce.exception.InvalidTokenException;
import com.example.ecommerce.exception.PasswordMismatchException;
import com.example.ecommerce.exception.UserNotFoundException;
import com.example.ecommerce.model.dto.ResetPasswordRequest;
import com.example.ecommerce.model.entity.User;
import com.example.ecommerce.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class PasswordResetService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender emailSender;

    @Value("${app.password-reset.token-expiration}")
    private long tokenExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder, JavaMailSender emailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    public void initiatePasswordReset(String email) {
        logger.info("Password reset initiated for email: {}", email);

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            // Generate a secure random token
            String token = generateSecureToken();
            logger.debug("Generated reset token for user: {}", email);

            // Set token expiration (e.g., 1 hour from now)
            user.setResetToken(token);
            user.setResetTokenExpiry(Instant.now().plusMillis(tokenExpirationMs));
            userRepository.save(user);
            logger.debug("Reset token saved for user: {}", email);

            // Send reset email
            sendResetEmail(user.getEmail(), token);
        } catch (Exception e) {
            logger.error("Error during password reset initiation", e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        // Validate token and get user
        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        // Validate token hasn't expired
        if (!user.isResetTokenValid()) {
            throw new InvalidTokenException("Token has expired");
        }

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Clear reset token
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
    }

    private String generateSecureToken() {
        return UUID.randomUUID().toString();
    }

    private void sendResetEmail(String email, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            logger.debug("Reset link generated: {}", resetLink);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setFrom("e-commerce@e-commerce.com");
            message.setSubject("Password Reset Request");
            message.setText("Click the following link to reset your password: " + resetLink +
                    "\n\nThis link will expire in 1 hour.");

            logger.debug("Attempting to send email to: {}", email);
            emailSender.send(message);
            logger.info("Password reset email sent successfully to: {}", email);
        } catch (Exception e) {
            logger.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage());
        }
    }
}
