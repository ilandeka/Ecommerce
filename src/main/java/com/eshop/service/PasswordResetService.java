package com.eshop.service;

import com.eshop.exception.InvalidTokenException;
import com.eshop.exception.PasswordMismatchException;
import com.eshop.exception.UserNotFoundException;
import com.eshop.model.dto.ResetPasswordRequest;
import com.eshop.model.entity.User;
import com.eshop.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    @Value("${spring.mail.properties.mail.from.name}")
    private String senderName;

    @Value("${spring.mail.properties.mail.from.address}")
    private String senderEmail;

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

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setFrom(String.format("%s <%s>", senderName, senderEmail));
            helper.setSubject("Password Reset Request");
            helper.setText(buildResetEmailContent(resetLink), true); // true enables HTML

            emailSender.send(message);
            logger.info("Password reset email sent successfully to: {}", email);

        } catch (Exception e) {
            logger.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    // HTML template string for our Password Reset
    private String buildResetEmailContent(String resetLink) {
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <title>Password Reset Request</title>" +
                "</head>" +
                "<body style=\"margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4; color: #333333;\">" +
                "   <table role=\"presentation\" style=\"width: 100%; border-collapse: collapse;\">" +
                "       <tr>" +
                "           <td style=\"padding: 20px 0; text-align: center; background-color: #0ea5e9;\">" +
                "               <h1 style=\"color: #ffffff; margin: 0; padding: 0; font-size: 24px;\">Password Reset Request</h1>" +
                "           </td>" +
                "       </tr>" +
                "   </table>" +
                "   <table role=\"presentation\" style=\"width: 100%; max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; margin-top: 20px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);\">" +
                "       <tr>" +
                "           <td style=\"padding: 40px;\">" +
                "               <p style=\"margin-bottom: 20px; font-size: 16px; line-height: 1.5;\">Hello,</p>" +
                "               <p style=\"margin-bottom: 20px; font-size: 16px; line-height: 1.5;\">We received a request to reset your password. If you didn't make this request, you can safely ignore this email.</p>" +
                "               <p style=\"margin-bottom: 30px; font-size: 16px; line-height: 1.5;\">To reset your password, click the button below:</p>" +
                "               <table role=\"presentation\" style=\"width: 100%; border-collapse: collapse;\">" +
                "                   <tr>" +
                "                       <td align=\"center\" style=\"padding: 20px 0;\">" +
                "                           <a href=\"%s\" style=\"background-color: #0ea5e9; color: #ffffff; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;font-size: 16px;\">" +
                "                               Reset Password" +
                "                           </a>" +
                "                       </td>" +
                "                   </tr>" +
                "               </table>" +
                "               <p style=\"margin-bottom: 20px; font-size: 16px; line-height: 1.5;\">This link will expire in 1 hour for security reasons. After that, you'll need to request a new password reset.</p>" +
                "               <p style=\"margin-bottom: 20px; font-size: 16px; line-height: 1.5;\">If you're having trouble clicking the button, copy and paste this link into your browser:</p>" +
                "               <p style=\"margin-bottom: 30px; font-size: 14px; line-height: 1.5; word-break: break-all; color: #666666;\">" +
                "                   %s" +
                "               </p>" +
                "               <p style=\"margin-bottom: 20px; font-size: 16px; line-height: 1.5;\">Best regards,<br>Your EShop Team</p>" +
                "           </td>" +
                "       </tr>" +
                "   </table>" +
                "   <table role=\"presentation\" style=\"width: 100%; max-width: 600px; margin: 20px auto; text-align: center;\">" +
                "       <tr>" +
                "           <td style=\"padding: 20px; color: #666666; font-size: 14px;\">" +
                "               <p style=\"margin: 0;\">This is an automated message, please do not reply.</p>" +
                "               <p style=\"margin: 10px 0 0 0;\">© 2025 EShop. All rights reserved.</p>" +
                "           </td>" +
                "       </tr>" +
                "   </table>" +
                "</body>" +
                "</html>";

        return html.replace("%s", resetLink);
    }
}
