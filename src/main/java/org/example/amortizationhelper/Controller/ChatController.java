package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.chat.ConversationIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";

    private final ChatClient chatClient;
    private final ConversationIdResolver conversationIdResolver;

    public ChatController(ChatClient chatClient, ConversationIdResolver conversationIdResolver) {
        this.chatClient = chatClient;
        this.conversationIdResolver = conversationIdResolver;
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(
            @RequestParam("message") String message,
            @RequestParam(name = "conversationId", required = false) String conversationId) {
        String clean = message.replaceAll("\\p{C}", "");
        String resolvedConversationId = conversationIdResolver.resolve(conversationId);
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User message (conversationId={}): {}", requestId, resolvedConversationId, clean);
        StringBuilder responseBuf = new StringBuilder();

        Flux<String> contentStream = chatClient.prompt()
                .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, resolvedConversationId))
                .user(clean)
                .stream()
                .content();

        StreamingResponseBody responseBody = outputStream -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                for (String chunk : contentStream.toIterable()) {
                    responseBuf.append(chunk);
                    writer.write(chunk);
                    writer.flush();
                }

                log.info("[{}] Assistant response (conversationId={}): {}",
                        requestId, resolvedConversationId, responseBuf);
            } catch (Exception e) {
                log.error("[{}] Chat stream error", requestId, e);
                throw e;
            }
        };

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(responseBody);
    }
}
