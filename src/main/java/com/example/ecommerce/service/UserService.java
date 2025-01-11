package com.example.ecommerce.service;

import com.example.ecommerce.exception.AuthenticationException;
import com.example.ecommerce.model.entity.User;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(Long userId,
                               String currentPassword,
                               String newPassword,
                               String confirmPassword) {
        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AuthenticationException("Current password is incorrect");
        }

        // Verify password confirmation
        if (!newPassword.equals(confirmPassword)) {
            throw new AuthenticationException("New password and confirmation do not match");
        }

        // Validate new password is different from current
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new AuthenticationException("New password must be different from current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}