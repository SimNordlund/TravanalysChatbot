package org.example.amortizationhelper.Email;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailTools {

    private final EmailService emailService;

    @Tool(
            name = "send_email",
            description = "Send a plain text email to the recipient only after the user explicitly asked to send it in this conversation."
    )
    public String sendEmail(
            @ToolParam(description = "Recipient email address exactly as provided by the user, for example test@sbab.se") String to,
            @ToolParam(description = "Short Swedish email subject") String subject,
            @ToolParam(description = "Plain text Swedish email body with race picks or winner suggestion") String body
    ) {

        emailService.sendPlainTextEmail(to, subject, body);
        return "Mejlet skickades till " + to + ".";
    }
}