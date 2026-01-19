package com.ezh.ezauth.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendWelcomeEmail(String to, String username) {
        try {
            Context context = new Context();
            context.setVariable("name", username);
            context.setVariable("actionUrl", "https://ez-hub.in/login");

            String htmlBody = templateEngine.process("email-welcome", context);
            sendHtmlMessage(to, "Welcome to EZ-Inventory!", htmlBody);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to {}", to, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        try {
            Context context = new Context();
            context.setVariable("resetUrl", "https://ez-hub.in/reset-password?token=" + token);

            String htmlBody = templateEngine.process("email-reset-password", context);
            sendHtmlMessage(to, "Reset Your Password", htmlBody);
        } catch (MessagingException e) {
            log.error("Failed to send reset email to {}", to, e);
        }
    }

    private void sendHtmlMessage(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        // MULTIPART_MODE_MIXED_RELATED is best for HTML + Images
        MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true indicates HTML

        mailSender.send(message);
        log.info("Email sent successfully to: {}", to);
    }
}