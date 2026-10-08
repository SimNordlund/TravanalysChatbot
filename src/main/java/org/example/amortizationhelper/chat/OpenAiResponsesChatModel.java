package org.example.amortizationhelper.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bridges Spring AI's ChatClient advisors and tools to OpenAI Responses API. */
public class OpenAiResponsesChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesChatModel.class);
    private static final int MAX_TOOL_ROUNDS = 24;
    private static final int MAX_TOOL_CALLS = 60;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String reasoningEffort;
    private final int maxOutputTokens;
    private final String promptCacheKey;
    private final Map<String, ToolCallback> callbacks;
    private final List<Map<String, Object>> toolDefinitions;

    public OpenAiResponsesChatModel(String apiKey, String model, String reasoningEffort,
                                    int maxOutputTokens, String promptCacheKey,
                                    ObjectMapper objectMapper, ToolCallback[] toolCallbacks) throws IOException {
        this.objectMapper = objectMapper;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.maxOutputTokens = maxOutputTokens;
        this.promptCacheKey = promptCacheKey;

        Map<String, ToolCallback> available = new LinkedHashMap<>();
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (ToolCallback callback : toolCallbacks) {
            var definition = callback.getToolDefinition();
            if (available.putIfAbsent(definition.name(), callback) != null) {
                throw new IllegalArgumentException("Duplicate tool name: " + definition.name());
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("type", "function");
            function.put("name", definition.name());
            if (definition.description() != null && !definition.description().isBlank()) {
                function.put("description", definition.description());
            }
            String schema = definition.inputSchema();
            function.put("parameters", schema == null || schema.isBlank()
                    ? Map.of("type", "object", "properties", Map.of()) : objectMapper.readTree(schema));
            function.put("strict", false);
            definitions.add(function);
        }
        this.callbacks = Map.copyOf(available);
        this.toolDefinitions = List.copyOf(definitions);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(180_000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        StringBuilder instructions = new StringBuilder();
        List<Object> input = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.SYSTEM) {
                if (!instructions.isEmpty()) instructions.append("\n\n");
                instructions.append(message.getText());
            } else if (message.getMessageType() == MessageType.USER) {
                input.add(Map.of("role", "user", "content", message.getText()));
            } else if (message.getMessageType() == MessageType.ASSISTANT) {
                if (message.getText() != null && !message.getText().isBlank()) {
                    input.add(Map.of("role", "assistant", "content", message.getText()));
                }
            }
        }

        Map<String, Object> toolContext = prompt.getOptions() instanceof ToolCallingChatOptions options
                && options.getToolContext() != null ? options.getToolContext() : Map.of();
        int toolCalls = 0;
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode response = request(instructions.toString(), input);
            if (!"completed".equals(response.path("status").asText())) {
                throw new IllegalStateException("OpenAI response did not complete: "
                        + response.path("status").asText("unknown"));
            }

            List<JsonNode> functionCalls = new ArrayList<>();
            StringBuilder answer = new StringBuilder();
            for (JsonNode item : response.path("output")) {
                if ("function_call".equals(item.path("type").asText())) {
                    functionCalls.add(item);
                } else if ("message".equals(item.path("type").asText())) {
                    for (JsonNode part : item.path("content")) {
                        if ("output_text".equals(part.path("type").asText())) {
                            answer.append(part.path("text").asText());
                        } else if ("refusal".equals(part.path("type").asText())) {
                            answer.append(part.path("refusal").asText());
                        }
                    }
                }
            }
            if (functionCalls.isEmpty()) {
                if (answer.isEmpty()) {
                    throw new IllegalStateException("OpenAI returned no answer or tool call");
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage(answer.toString()))));
            }
            toolCalls += functionCalls.size();
            if (toolCalls > MAX_TOOL_CALLS) {
                throw new IllegalStateException("OpenAI requested too many tool calls");
            }

            // In stateless mode the complete output, including encrypted reasoning,
            // must be replayed before the function results in the next request.
            for (JsonNode item : response.path("output")) {
                input.add(item);
            }
            for (JsonNode functionCall : functionCalls) {
                String name = functionCall.path("name").asText();
                String callId = functionCall.path("call_id").asText();
                ToolCallback callback = callbacks.get(name);
                if (callback == null || callId.isBlank()) {
                    throw new IllegalStateException("Unknown or incomplete OpenAI tool call: " + name);
                }
                String result;
                try {
                    result = callback.call(functionCall.path("arguments").asText("{}"),
                            new ToolContext(toolContext));
                } catch (RuntimeException e) {
                    log.warn("Travolta tool {} failed: {}", name, e.getClass().getSimpleName());
                    result = "Verktyget kunde inte hämta uppgiften just nu. Hitta inte på resultat.";
                }
                input.add(Map.of("type", "function_call_output", "call_id", callId,
                        "output", result == null ? "" : result));
            }
        }
        throw new IllegalStateException("OpenAI exceeded the tool round limit");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // Keep ChatClient's streaming endpoint and advisors available while the
        // Responses tool/reasoning loop runs to completion.
        return Flux.defer(() -> Flux.just(call(prompt)));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().model(model).build();
    }

    private JsonNode request(String instructions, List<Object> input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("reasoning", Map.of("effort", reasoningEffort));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("store", false);
        if (!toolDefinitions.isEmpty()) {
            body.put("tools", toolDefinitions);
            body.put("parallel_tool_calls", true);
        }
        if (promptCacheKey != null && !promptCacheKey.isBlank()) {
            body.put("prompt_cache_key", promptCacheKey);
        }
        try {
            String raw = restClient.post().uri("/responses")
                    .body(body).retrieve().body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty response");
            }
            return objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse OpenAI response", e);
        }
    }
}
