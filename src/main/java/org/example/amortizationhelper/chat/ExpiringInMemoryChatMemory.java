package org.example.amortizationhelper.chat;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExpiringInMemoryChatMemory implements ChatMemoryRepository {

    private final Duration ttl;
    private final Clock clock;
    private final Map<String, StoredConversation> conversations = new ConcurrentHashMap<>();

    public ExpiringInMemoryChatMemory(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    public ExpiringInMemoryChatMemory(Duration ttl, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public List<String> findConversationIds() {
        purgeExpired();
        return new ArrayList<>(conversations.keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        purgeExpired();
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }

        StoredConversation stored = conversations.get(conversationId);
        if (stored == null) {
            return List.of();
        }

        stored.touch(now());
        return stored.messages();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        purgeExpired();
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        conversations.put(conversationId, new StoredConversation(
                List.copyOf(messages == null ? List.of() : messages),
                now()
        ));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        conversations.remove(conversationId);
    }

    private void purgeExpired() {
        Instant cutoff = now().minus(ttl);
        conversations.entrySet().removeIf(entry -> entry.getValue().lastTouched().isBefore(cutoff));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static final class StoredConversation {
        private final List<Message> messages;
        private volatile Instant lastTouched;

        private StoredConversation(List<Message> messages, Instant lastTouched) {
            this.messages = messages;
            this.lastTouched = lastTouched;
        }

        private List<Message> messages() {
            return messages;
        }

        private Instant lastTouched() {
            return lastTouched;
        }

        private void touch(Instant instant) {
            this.lastTouched = instant;
        }
    }
}
