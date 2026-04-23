package org.example.amortizationhelper.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackAwareGeocodingToolCallbackTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rewritesTrackNameToWeatherCityBeforeCallingDelegate() {
        CapturingToolCallback delegate = new CapturingToolCallback();
        TrackAwareGeocodingToolCallback callback = new TrackAwareGeocodingToolCallback(delegate, objectMapper);

        callback.call("""
                {"name":"Solvalla, Stockholm, Sweden","countryCode":"SE","count":3}
                """);

        assertEquals("{\"name\":\"Stockholm\",\"countryCode\":\"SE\",\"count\":3}", delegate.lastInput);
    }

    @Test
    void rewritesMisspelledTrackNameToWeatherCityBeforeCallingDelegate() {
        CapturingToolCallback delegate = new CapturingToolCallback();
        TrackAwareGeocodingToolCallback callback = new TrackAwareGeocodingToolCallback(delegate, objectMapper);

        callback.call("""
                {"name":"Färejstad, Karlstad, Sweden","countryCode":"SE","count":3}
                """);

        assertEquals("{\"name\":\"Karlstad\",\"countryCode\":\"SE\",\"count\":3}", delegate.lastInput);
    }

    private static class CapturingToolCallback implements ToolCallback {
        private String lastInput;

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name("spring_ai_mcp_client_weather_geocoding")
                    .description("geocoding")
                    .inputSchema("{}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            this.lastInput = toolInput;
            return "{}";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }
}
