package org.example.amortizationhelper.voice;

import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@Service
public class AzureSpeechTtsService {

    private static final String SWEDISH_LOCALE = "sv-SE";

    private final String speechKey;
    private final String speechRegion;
    private final String defaultVoice;

    public AzureSpeechTtsService(
            @Value("${app.azure.speech.key}") String speechKey,
            @Value("${app.azure.speech.region}") String speechRegion,
            @Value("${app.azure.speech.voice}") String defaultVoice
    ) {
        this.speechKey = speechKey;
        this.speechRegion = speechRegion;
        this.defaultVoice = defaultVoice;
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

        try (SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion)) {
            speechConfig.setSpeechSynthesisLanguage(SWEDISH_LOCALE);
            speechConfig.setSpeechSynthesisVoiceName(voice);
            speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Audio24Khz160KBitRateMonoMp3);

            try (SpeechSynthesizer synthesizer = new SpeechSynthesizer(speechConfig, null)) {
                SpeechSynthesisResult result = synthesize(synthesizer, text, voice, speed);
                try {
                    if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
                        return result.getAudioData();
                    }

                    if (result.getReason() == ResultReason.Canceled) {
                        SpeechSynthesisCancellationDetails details = SpeechSynthesisCancellationDetails.fromResult(result);
                        String errorDetails = details.getErrorDetails();
                        String suffix = (errorDetails == null || errorDetails.isBlank()) ? "" : ": " + errorDetails;
                        if (details.getReason() == CancellationReason.Error) {
                            throw new IOException("Azure Speech synthesis failed" + suffix);
                        }
                        throw new IOException("Azure Speech synthesis canceled: " + details.getReason() + suffix);
                    }

                    throw new IOException("Azure Speech synthesis failed with reason: " + result.getReason());
                } finally {
                    result.close();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Azure Speech synthesis was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IOException("Azure Speech synthesis failed.", e);
        }
    }

    private SpeechSynthesisResult synthesize(SpeechSynthesizer synthesizer, String text, String voice, float speed)
            throws ExecutionException, InterruptedException {
        if (Math.abs(speed - 1.0f) < 0.01f) {
            return synthesizer.SpeakTextAsync(text).get();
        }

        return synthesizer.SpeakSsmlAsync(buildSsml(text, voice, speed)).get();
    }

    private String resolveVoice(String requestedVoice) {
        if (requestedVoice != null && requestedVoice.toLowerCase(Locale.ROOT).startsWith("sv-se-")) {
            return requestedVoice;
        }
        return defaultVoice;
    }

    private static String buildSsml(String text, String voice, float speed) {
        return """
                <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="sv-SE">
                    <voice name="%s">
                        <prosody rate="%s">%s</prosody>
                    </voice>
                </speak>
                """.formatted(escapeXml(voice), speedToRate(speed), escapeXml(text));
    }

    private static String speedToRate(float speed) {
        int percent = Math.round((speed - 1.0f) * 100);
        percent = Math.max(-50, Math.min(100, percent));
        return percent >= 0 ? "+" + percent + "%" : percent + "%";
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
