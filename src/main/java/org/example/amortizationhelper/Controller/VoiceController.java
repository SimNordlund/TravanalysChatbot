package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.chat.ConversationIdResolver;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/voice")
@CrossOrigin(origins = "*")
public class VoiceController {

    private static final float DEFAULT_SPEED = 1.0f;
    private static final float MIN_SPEED = 0.25f;
    private static final float MAX_SPEED = 4.0f;
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";

    private final OpenAiAudioTranscriptionModel sttModel;
    private final OpenAiAudioSpeechModel ttsModel;
    private final ChatClient chatClient;
    private final ConversationIdResolver conversationIdResolver;

    public VoiceController(OpenAiAudioTranscriptionModel sttModel,
                           OpenAiAudioSpeechModel ttsModel,
                           ChatClient chatClient,
                           ConversationIdResolver conversationIdResolver) {
        this.sttModel = sttModel;
        this.ttsModel = ttsModel;
        this.chatClient = chatClient;
        this.conversationIdResolver = conversationIdResolver;
    }

    @PostMapping(
            value = "/chat",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> chatWithAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "voice", defaultValue = "ASH") String voiceName,
            @RequestParam(name = "speed", defaultValue = "1.0") float speed,
            @RequestParam(name = "conversationId", required = false) String conversationId
    ) throws IOException {

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : ".webm";
        Path tmp = Files.createTempFile("voice-", ext);
        file.transferTo(tmp);

        try {
            var trOpts = OpenAiAudioTranscriptionOptions.builder()
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                    .language("sv")
                    .temperature(0f)
                    .build();

            var trPrompt = new AudioTranscriptionPrompt(new FileSystemResource(tmp.toFile()), trOpts);
            var trResp = sttModel.call(trPrompt);
            String userText = trResp.getResult().getOutput();
            String resolvedConversationId = conversationIdResolver.resolve(conversationId);

            String answerText = chatClient.prompt()
                    .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, resolvedConversationId))
                    .system("""
                                Du är i röstläge. Svara kort, utan markdown, på tydlig svenska.
                                Korta meningar. Max tre punkter i listor. Säg "procent" istället för % om uttalet blir otydligt.
                            """)
                    .user(userText == null ? "" : userText)
                    .call()
                    .content();

            if (answerText == null || answerText.isBlank()) {
                answerText = "Jag fick inget svar att läsa upp.";
            }

            var speechOpts = OpenAiAudioSpeechOptions.builder()
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .voice(resolveVoice(voiceName))
                    .speed(normalizeSpeed(speed))
                    .build();

            var speechResp = ttsModel.call(new SpeechPrompt(answerText, speechOpts));
            byte[] mp3 = speechResp.getResult().getOutput();

            return ResponseEntity.ok(Map.of(
                    "text", answerText,
                    "audioBase64", Base64.getEncoder().encodeToString(mp3)
            ));
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestPart("file") MultipartFile file) throws IOException {

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : ".webm";
        Path tmp = Files.createTempFile("stt-", ext);
        file.transferTo(tmp.toFile());

        try {
            var trOpts = OpenAiAudioTranscriptionOptions.builder()
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                    .language("sv")
                    .temperature(0f)
                    .build();

            var prompt = new AudioTranscriptionPrompt(new FileSystemResource(tmp.toFile()), trOpts);
            var resp = sttModel.call(prompt);
            String text = resp.getResult().getOutput();

            return ResponseEntity.ok(Map.of("text", text == null ? "" : text));
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    @PostMapping(
            value = "/tts",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> tts(@RequestBody TtsRequest req) throws IOException {
        String text = req.text() == null ? "" : req.text();

        var opts = OpenAiAudioSpeechOptions.builder()
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .voice(resolveVoice(req.voice()))
                .speed(normalizeSpeed(req.speed() == null ? DEFAULT_SPEED : req.speed()))
                .build();

        var speech = ttsModel.call(new SpeechPrompt(text, opts));
        byte[] mp3 = speech.getResult().getOutput();

        return ResponseEntity.ok(Map.of(
                "audioBase64", Base64.getEncoder().encodeToString(mp3)
        ));
    }

    public record TtsRequest(String text, String voice, Float speed) {
    }

    private OpenAiAudioApi.SpeechRequest.Voice resolveVoice(String voiceName) {
        String voice = (voiceName == null || voiceName.isBlank()) ? "ALLOY" : voiceName;
        try {
            return OpenAiAudioApi.SpeechRequest.Voice.valueOf(voice.toUpperCase());
        } catch (Exception e) {
            return OpenAiAudioApi.SpeechRequest.Voice.ALLOY;
        }
    }

    private float normalizeSpeed(float speed) {
        if (Float.isNaN(speed) || Float.isInfinite(speed)) {
            return DEFAULT_SPEED;
        }
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }
}
