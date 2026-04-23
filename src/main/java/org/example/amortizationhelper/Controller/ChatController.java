package org.example.amortizationhelper.Controller;

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

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/chat-stream")
    public Flux<String> chatStream(@RequestParam("message") String message) {
        String clean = message.replaceAll("\\p{C}", "");
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User message: {}", requestId, clean);
        StringBuilder responseBuf = new StringBuilder();

        return chatClient.prompt()
                .user(clean)
                .stream()
                .content()
                .doOnNext(responseBuf::append)
                .doOnComplete(() -> log.info("[{}] Assistant response: {}", requestId, responseBuf))
                .doOnError(e -> log.error("[{}] Chat stream error", requestId, e));
    }
}
