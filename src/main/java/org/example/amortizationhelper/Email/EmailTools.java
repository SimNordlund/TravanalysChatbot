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
            description = "Send a plain text email only when the user has explicitly asked to send it now. Use for horse picks, top 3 horses, race summaries, or betting summaries."
    )
    public String sendEmail(
                             @ToolParam(description = "Recipient email address exactly as provided by the user, for example test@sbab.se") String to,
                             @ToolParam(description = "Short Swedish email subject") String subject,
                             @ToolParam(description = "Plain text Swedish email body with the race result, top 3 horses, or winner suggestion") String body,
                             @ToolParam(description = "Must be true only if the user explicitly asked to send the email now in this conversation") boolean confirmedByUser
    ) {

        if (!confirmedByUser) {
            return "Mejlet skickades inte eftersom användaren inte uttryckligen bad om att skicka det nu.";
        }

        emailService.sendPlainTextEmail(to, subject, body);
        return "Mejlet skickades till " + to + ".";
    }
}
