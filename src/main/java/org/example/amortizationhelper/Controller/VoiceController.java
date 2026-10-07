package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.chat.ConversationIdResolver;
import org.example.amortizationhelper.chat.TravoltaPromptService;
import org.example.amortizationhelper.voice.AzureSpeechTtsService;
import org.example.amortizationhelper.voice.SwedishSpeechText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);
    private static final float DEFAULT_SPEED = 1.0f;
    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 2.0f;
    private static final long MAX_AUDIO_BYTES = 25L * 1024 * 1024;
    private static final int MAX_TTS_CHARACTERS = 8_000;
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".flac", ".m4a", ".mp3", ".mp4", ".mpeg", ".mpga", ".ogg", ".wav", ".webm");
    private static final String TRANSCRIPTION_CONTEXT = """
            Samtalet är på svenska och handlar om trav och webbplatsen Travanalys.
            Namn och fackord som kan förekomma: Travolta, Travanalys, ATG, Svensk Travsport,
            V75, V85, V86, V64, V65, GS75, V5, V4, Dagens Dubbel, startlista, startnummer,
            avdelning, lopp, bana, kusk, tränare, spik, gardering, skräll, streckprocent,
            vinstchans, spelvärde, odds, utdelning, autostart, voltstart, barfota och jänkarvagn.
            Travbanor: Solvalla, Åby, Jägersro, Färjestad, Bergsåker, Axevalla, Halmstad,
            Romme, Boden, Gävle, Bollnäs, Eskilstuna, Kalmar, Mantorp och Örebro.
            """;

    private final OpenAiAudioTranscriptionModel sttModel;
    private final AzureSpeechTtsService ttsService;
    private final ChatClient chatClient;
    private final ConversationIdResolver conversationIdResolver;
    private final TravoltaPromptService promptService;

    public VoiceController(OpenAiAudioTranscriptionModel sttModel,
                           AzureSpeechTtsService ttsService,
                           ChatClient chatClient,
                           ConversationIdResolver conversationIdResolver,
                           TravoltaPromptService promptService) {
        this.sttModel = sttModel;
        this.ttsService = ttsService;
        this.chatClient = chatClient;
        this.conversationIdResolver = conversationIdResolver;
        this.promptService = promptService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chatWithAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "voice", required = false) String voiceName,
            @RequestParam(name = "speed", defaultValue = "1.0") float speed,
            @RequestParam(name = "conversationId", required = false) String conversationId
    ) throws IOException {
        String userText = transcribeAudio(file);
        String resolvedConversationId = conversationIdResolver.resolve(conversationId);
        String answerText;

        if (userText.isBlank()) {
            // Silence must not become an empty prompt that invents a new conversation turn.
            answerText = "Jag hörde ingen tydlig fråga. Försök gärna igen och prata lite närmare mikrofonen.";
        } else {
            answerText = chatClient.prompt()
                    .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, resolvedConversationId))
                    .system(promptService.forVoice())
                    .user(userText)
                    .call()
                    .content();

            if (answerText == null || answerText.isBlank()) {
                answerText = "Jag kunde inte ta fram ett svar just nu. Försök gärna igen.";
            }
        }

        Map<String, Object> response = speechResponse(answerText, voiceName, speed);
        response.put("transcript", userText);
        response.put("speechDetected", !userText.isBlank());
        response.put("conversationId", resolvedConversationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> transcribe(@RequestPart("file") MultipartFile file)
            throws IOException {
        String text = transcribeAudio(file);
        return ResponseEntity.ok(Map.of("text", text, "speechDetected", !text.isBlank()));
    }

    @PostMapping(value = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> tts(@RequestBody TtsRequest req) {
        String text = req.text() == null ? "" : req.text().trim();
        if (text.length() > MAX_TTS_CHARACTERS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Texten är för lång att läsa upp. Välj högst " + MAX_TTS_CHARACTERS + " tecken.");
        }
        if (text.isBlank() || SwedishSpeechText.clean(text).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skriv en text att läsa upp.");
        }
        return ResponseEntity.ok(speechResponse(text, req.voice(),
                req.speed() == null ? DEFAULT_SPEED : req.speed()));
    }

    public record TtsRequest(String text, String voice, Float speed) {
    }

    private String transcribeAudio(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inspelningen är tom. Spela in frågan igen.");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Inspelningen är för stor. Spela in en kortare fråga.");
        }

        Path tmp = Files.createTempFile("travolta-voice-", audioExtension(file));
        try {
            file.transferTo(tmp);
            var options = OpenAiAudioTranscriptionOptions.builder()
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                    .language("sv")
                    .prompt(TRANSCRIPTION_CONTEXT)
                    .temperature(0f)
                    .build();
            var response = sttModel.call(new AudioTranscriptionPrompt(new FileSystemResource(tmp), options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return "";
            }
            return response.getResult().getOutput().trim();
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                log.warn("Could not remove temporary voice recording: {}", e.getClass().getSimpleName());
            }
        }
    }

    private Map<String, Object> speechResponse(String text, String voice, float speed) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("text", text);
        response.put("audioBase64", "");
        response.put("audioAvailable", false);
        try {
            byte[] mp3 = ttsService.synthesizeMp3(SwedishSpeechText.clean(text), voice, normalizeSpeed(speed));
            if (mp3.length > 0) {
                response.put("audioBase64", Base64.getEncoder().encodeToString(mp3));
                response.put("audioAvailable", true);
            } else {
                response.put("audioError", "Svaret visas som text, men kunde inte läsas upp.");
            }
        } catch (IOException e) {
            // The useful answer survives an audio-provider outage; keep existing JSON keys for clients.
            log.warn("Travolta speech synthesis unavailable: {}", e.getClass().getSimpleName());
            response.put("audioError", "Uppläsningen är tillfälligt otillgänglig. Du kan läsa svaret i chatten.");
        }
        return response;
    }

    private static String audioExtension(MultipartFile file) {
        String contentType = file.getContentType();
        String mime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        // Browsers may send an MP4 recording with an input.webm filename. Trust a known media type first.
        String extension = switch (mime) {
            case "audio/webm", "video/webm" -> ".webm";
            case "audio/mp4", "video/mp4", "audio/m4a", "audio/x-m4a" -> ".mp4";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/ogg", "application/ogg" -> ".ogg";
            case "audio/wav", "audio/wave", "audio/x-wav" -> ".wav";
            case "audio/flac", "audio/x-flac" -> ".flac";
            default -> null;
        };
        if (extension != null) {
            return extension;
        }

        if (mime.isEmpty() || mime.equals("application/octet-stream") || mime.startsWith("audio/")) {
            String filename = file.getOriginalFilename();
            if (filename != null && filename.contains(".")) {
                String suffix = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                if (AUDIO_EXTENSIONS.contains(suffix)) {
                    return suffix;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Ljudformatet stöds inte. Använd exempelvis WebM, MP4, MP3 eller WAV.");
    }

    private static float normalizeSpeed(float speed) {
        if (!Float.isFinite(speed)) {
            return DEFAULT_SPEED;
        }
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }
}
