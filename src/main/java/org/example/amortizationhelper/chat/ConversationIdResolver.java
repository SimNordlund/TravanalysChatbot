package org.example.amortizationhelper.chat;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ConversationIdResolver {

    private static final int MAX_LENGTH = 120;
    private static final Pattern SAFE_PATTERN = Pattern.compile("[A-Za-z0-9:_-]{1,120}");

    public String resolve(String conversationId) {
        if (conversationId == null) {
            return newConversationId();
        }

        String trimmed = conversationId.trim();
        if (trimmed.isEmpty()) {
            return newConversationId();
        }

        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }

        if (!SAFE_PATTERN.matcher(trimmed).matches()) {
            return newConversationId();
        }

        return trimmed;
    }

    private String newConversationId() {
        return "anon-" + UUID.randomUUID();
    }
}
