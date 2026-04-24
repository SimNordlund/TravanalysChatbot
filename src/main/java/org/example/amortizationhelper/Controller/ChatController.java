package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.chat.ConversationIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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

    @GetMapping("/chat-stream")
    public Flux<String> chatStream(@RequestParam("message") String message,
                                   @RequestParam(name = "conversationId", required = false) String conversationId) {
        String clean = message.replaceAll("\\p{C}", "");
        String resolvedConversationId = conversationIdResolver.resolve(conversationId);
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User message (conversationId={}): {}", requestId, resolvedConversationId, clean);
        StringBuilder responseBuf = new StringBuilder();

        return chatClient.prompt()
                .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, resolvedConversationId))
                .user(clean)
                .stream()
                .content()
                .doOnNext(responseBuf::append)
                .doOnComplete(() -> log.info("[{}] Assistant response (conversationId={}): {}",
                        requestId, resolvedConversationId, responseBuf))
                .doOnError(e -> log.error("[{}] Chat stream error", requestId, e));
    }
}
