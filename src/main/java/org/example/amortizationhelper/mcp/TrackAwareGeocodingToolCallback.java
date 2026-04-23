package org.example.amortizationhelper.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class TrackAwareGeocodingToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(TrackAwareGeocodingToolCallback.class);

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;

    public TrackAwareGeocodingToolCallback(ToolCallback delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(rewriteTrackLocation(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(rewriteTrackLocation(toolInput), toolContext);
    }

    private String rewriteTrackLocation(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return toolInput;
        }

        try {
            JsonNode root = objectMapper.readTree(toolInput);
            if (!(root instanceof ObjectNode objectNode)) {
                return toolInput;
            }

            JsonNode nameNode = objectNode.get("name");
            if (nameNode == null || !nameNode.isTextual()) {
                return toolInput;
            }

            TrackWeatherMapper.WeatherLocation location = TrackWeatherMapper.resolve(nameNode.asText());
            if (!location.knownTrack() || location.city().isBlank()) {
                return toolInput;
            }

            ObjectNode rewritten = objectNode.deepCopy();
            rewritten.put("name", location.city());

            String countryCode = countryCode(location.country());
            if (!countryCode.isBlank()) {
                rewritten.put("countryCode", countryCode);
            }

            String rewrittenInput = objectMapper.writeValueAsString(rewritten);
            log.debug("Rewrote weather geocoding input from {} to {}", toolInput, rewrittenInput);
            return rewrittenInput;
        } catch (Exception e) {
            log.debug("Could not rewrite weather geocoding input: {}", toolInput, e);
            return toolInput;
        }
    }

    private String countryCode(String country) {
        if (country == null) {
            return "";
        }

        return switch (country) {
            case "Sweden" -> "SE";
            case "Norway" -> "NO";
            case "Denmark" -> "DK";
            case "Finland" -> "FI";
            default -> "";
        };
    }
}
