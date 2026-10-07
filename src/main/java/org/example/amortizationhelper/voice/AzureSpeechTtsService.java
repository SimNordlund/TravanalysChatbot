package org.example.amortizationhelper.voice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AzureSpeechTtsService {

    private static final String SWEDISH_LOCALE = "sv-SE";
    private static final String FALLBACK_VOICE = "sv-SE-MattiasNeural";

    private final String speechKey;
    private final String speechRegion;
    private final String defaultVoice;
    private final RestClient restClient;

    public AzureSpeechTtsService(
            @Value("${app.azure.speech.key:}") String speechKey,
            @Value("${app.azure.speech.region:westeurope}") String speechRegion,
            @Value("${app.azure.speech.voice:sv-SE-MattiasNeural}") String defaultVoice
    ) {
        this.speechKey = speechKey;
        this.speechRegion = speechRegion;
        this.defaultVoice = defaultVoice;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(45_000);
        this.restClient = RestClient.builder()
                .baseUrl("https://" + speechRegion + ".tts.speech.microsoft.com")
                .requestFactory(requestFactory)
                .build();
    }

    public byte[] synthesizeMp3(String text, String requestedVoice, float speed) throws IOException {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        if (speechKey == null || speechKey.isBlank()) {
            throw new IOException("Azure Speech key is missing. Set AZURE_SPEECH_KEY.");
        }
        if (speechRegion == null || speechRegion.isBlank()) {
            throw new IOException("Azure Speech region is missing. Set AZURE_SPEECH_REGION.");
        }

        String voice = resolveVoice(requestedVoice);
        String ssml = buildSsml(text, voice, speed);

        try {
            byte[] audio = restClient.post()
                    .uri("/cognitiveservices/v1")
                    .header("Ocp-Apim-Subscription-Key", speechKey)
                    .header("X-Microsoft-OutputFormat", "audio-24khz-160kbitrate-mono-mp3")
                    .header("User-Agent", "Travolta")
                    .contentType(new MediaType("application", "ssml+xml", StandardCharsets.UTF_8))
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .body(ssml.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new IOException("Azure Speech returned empty audio.");
            }
            return audio;
        } catch (RestClientResponseException e) {
            throw new IOException(
                    "Azure Speech request failed with HTTP " + e.getStatusCode().value()
                            + ".",
                    e
            );
        } catch (RestClientException e) {
            throw new IOException("Azure Speech request failed.", e);
        }
    }

    private String resolveVoice(String requestedVoice) {
        String voice = validSwedishVoice(requestedVoice);
        if (voice != null) {
            return voice;
        }
        String configuredVoice = validSwedishVoice(defaultVoice);
        return configuredVoice != null ? configuredVoice : FALLBACK_VOICE;
    }

    private static String validSwedishVoice(String voice) {
        if (voice == null) return null;
        String trimmed = voice.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("sv-se-")
                && trimmed.substring(6).matches("[A-Za-z][A-Za-z0-9]*Neural")) {
            return "sv-SE-" + trimmed.substring(6);
        }
        return null;
    }

    private static String buildSsml(String text, String voice, float speed) {
        return """
                <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="%s">
                    <voice name="%s">
                        <prosody rate="%s">%s</prosody>
                    </voice>
                </speak>
                """.formatted(SWEDISH_LOCALE, escapeXml(voice), speedToRate(speed),
                escapeXml(text).replace("\n", "<break time=\"220ms\"/>"));
    }

    private static String speedToRate(float speed) {
        if (!Float.isFinite(speed)) {
            speed = 1.0f;
        }
        int percent = Math.round((speed - 1.0f) * 100);
        percent = Math.max(-50, Math.min(100, percent));
        return percent >= 0 ? "+" + percent + "%" : percent + "%";
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        StringBuilder validXml = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                        || codePoint >= 0x20 && codePoint <= 0xD7FF
                        || codePoint >= 0xE000 && codePoint <= 0xFFFD
                        || codePoint >= 0x10000 && codePoint <= 0x10FFFF)
                .forEach(validXml::appendCodePoint);
        return validXml.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
