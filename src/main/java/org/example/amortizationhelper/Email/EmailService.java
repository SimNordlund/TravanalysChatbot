package org.example.amortizationhelper.Email;

import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    public void sendPlainTextEmail(String to, String subject, String body) {
        validateEmail(to);

        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Mail from address is not configured.");
        }

        String cleanSubject = subject == null ? "" : subject.trim();
        String cleanBody = body == null ? "" : body.trim();

        if (cleanSubject.isBlank()) {
            cleanSubject = "Trav-olta";
        }

        if (cleanBody.isBlank()) {
            throw new IllegalArgumentException("Email body must not be blank.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.trim());
        message.setSubject(cleanSubject);
        message.setText(cleanBody);

        mailSender.send(message);
    }

    private void validateEmail(String email) {
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid email address: " + email, e);
        }
    }
}
