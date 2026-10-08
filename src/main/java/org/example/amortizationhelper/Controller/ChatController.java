package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.chat.ConversationIdResolver;
import org.example.amortizationhelper.chat.TravoltaPromptService;
import org.example.amortizationhelper.chat.TravoltaScopeGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final String STREAM_ERROR_MESSAGE =
            "Svaret avbröts innan jag blev klar. Försök gärna igen så kontrollerar jag uppgifterna på nytt.";
    private static final String EMPTY_RESPONSE_MESSAGE =
            "Jag kunde inte ta fram ett svar just nu. Försök gärna igen.";

    private final ChatClient chatClient;
    private final ConversationIdResolver conversationIdResolver;
    private final TravoltaPromptService promptService;
    private final TravoltaScopeGuard scopeGuard;

    public ChatController(ChatClient chatClient, ConversationIdResolver conversationIdResolver,
                          TravoltaPromptService promptService, TravoltaScopeGuard scopeGuard) {
        this.chatClient = chatClient;
        this.conversationIdResolver = conversationIdResolver;
        this.promptService = promptService;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(
            @RequestParam("message") String message,
            @RequestParam(name = "conversationId", required = false) String conversationId) {
        // Keep line breaks: numbered selections and reducer inputs must not be joined together.
        String clean = message.replaceAll("[\\p{C}&&[^\\n\\r\\t]]", "").trim();
        if (clean.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skriv en fråga till Travolta.");
        }
        if (clean.length() > 12_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Frågan är för lång. Dela gärna upp den i kortare delar.");
        }
        String resolvedConversationId = conversationIdResolver.resolve(conversationId);
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Chat request ({} characters)", requestId, clean.length());
        String blockedResponse = scopeGuard.blockReason(clean, resolvedConversationId);
        if (blockedResponse != null) {
            StreamingResponseBody responseBody = outputStream -> {
                try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    writer.write(blockedResponse);
                    writer.flush();
                }
            };
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                    .header("X-Accel-Buffering", "no")
                    .header("X-Conversation-Id", resolvedConversationId)
                    .body(responseBody);
        }
        StringBuilder responseBuf = new StringBuilder();

        Flux<String> contentStream = chatClient.prompt()
                .system(promptService.forText())
                .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, resolvedConversationId))
                .user(clean)
                .stream()
                .content();

        StreamingResponseBody responseBody = outputStream -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                try {
                    for (String chunk : contentStream.toIterable()) {
                        responseBuf.append(chunk);
                        writer.write(chunk);
                        writer.flush();
                    }

                    if (responseBuf.toString().isBlank()) {
                        writer.write(EMPTY_RESPONSE_MESSAGE);
                        writer.flush();
                    }
                    log.info("[{}] Chat response completed ({} characters)", requestId, responseBuf.length());
                } catch (Exception e) {
                    log.error("[{}] Chat stream error", requestId, e);
                    if (!responseBuf.isEmpty()) {
                        writer.write(System.lineSeparator());
                        writer.write(System.lineSeparator());
                    }
                    writer.write(STREAM_ERROR_MESSAGE);
                    writer.flush();
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .header("X-Conversation-Id", resolvedConversationId)
                .body(responseBody);
    }
}
