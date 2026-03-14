package org.example.amortizationhelper.WebSearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiWebSearchService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiWebSearchService(
                                   @Value("${spring.ai.openai.api-key}") String apiKey,
                                   @Value("${app.openai.web-search.model:gpt-5.4}") String model,
                                   ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String search(String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("tools", List.of(Map.of("type", "web_search")));
        body.put("input", buildSearchPrompt(query));

        String rawResponse = restClient.post()
                .uri("/responses")
                .body(body)
                .retrieve()
                .body(String.class);

        return extractText(rawResponse);
    }

    private String buildSearchPrompt(String query) {
        return """ 
                Search the live web and answer in Swedish. 
                Prefer recent, reliable sources. 
                Be concise but useful. 
                User question: %s 
                """.formatted(query == null ? "" : query);
    }

    private String extractText(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "Jag kunde inte hämta något svar ifrån webben just nu.";
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            String outputText = root.path("output_text").asText("");
            if (!outputText.isBlank()) {
                return outputText.trim();
            }

            JsonNode output = root.path("output");
            if (output.isArray()) {
                StringBuilder sb = new StringBuilder();

                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (!content.isArray()) {
                        continue;
                    }

                    for (JsonNode contentItem : content) {
                        String text = contentItem.path("text").asText("");
                        if (!text.isBlank()) {
                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(text.trim());
                        }
                    }
                }

                if (sb.length() > 0) {
                    return sb.toString().trim();
                }
            }

            return "Jag kunde inte tolka webbsökningssvaret";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenAI web search response", e);
        }
    }
}