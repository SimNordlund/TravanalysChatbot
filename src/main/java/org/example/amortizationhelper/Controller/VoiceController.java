package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Tools.RoiTools;
import org.example.amortizationhelper.Tools.StartlistaTools;
import org.example.amortizationhelper.Tools.TravTools;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/voice")
@CrossOrigin(origins = "*")
public class VoiceController {

    private final OpenAiAudioTranscriptionModel sttModel;
    private final OpenAiAudioSpeechModel ttsModel;
    private final ChatClient chatClient;

    @Autowired
    public VoiceController(OpenAiAudioTranscriptionModel sttModel,
                           OpenAiAudioSpeechModel ttsModel,
                           ChatClient.Builder builder,
                           VectorStore vectorStore,
                           ResourceLoader resourceLoader,
                           TravTools travTools,
                           StartlistaTools startlistaTools,
                           RoiTools roiTools) throws Exception {
        this.sttModel = sttModel;
        this.ttsModel = ttsModel;

        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .build();

        Resource promptRes = resourceLoader.getResource("classpath:/prompts/travPrompt.st");
        String templateString;
        try (var in = promptRes.getInputStream()) {
            templateString = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }

        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template(templateString)
                .build();

        var queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .promptTemplate(template)
                .build();

        var ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter)
                .build();

        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(12)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();

        this.chatClient = builder
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, roiTools)
                .build();
    }

    @PostMapping(
            value = "/chat",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> chatWithAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "voice", defaultValue = "NOVA") String voiceName,
            @RequestParam(name = "speed", defaultValue = "1.0") float speed
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

            String answerText = chatClient.prompt()
                    .system("""
                        Du är i röstläge. Svara kort, utan markdown, på tydlig svenska.
                        Korta meningar. Max tre punkter i listor. Säg "procent" istället för % om uttalet blir otydligt.
                    """)
                    .user(userText == null ? "" : userText)
                    .call()
                    .content();

            OpenAiAudioApi.SpeechRequest.Voice voiceEnum;
            try { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.valueOf(voiceName.toUpperCase()); }
            catch (Exception e) { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.ALLOY; }

            var speechOpts = OpenAiAudioSpeechOptions.builder()
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .voice(voiceEnum)
                    .speed(speed)
                    .build();

            var speechResp = ttsModel.call(new SpeechPrompt(answerText, speechOpts));
            byte[] mp3 = speechResp.getResult().getOutput();

            return ResponseEntity.ok(Map.of(
                    "text", answerText,
                    "audioBase64", Base64.getEncoder().encodeToString(mp3)
            ));
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
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
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }

    public static record TtsRequest(String text, String voice, Float speed) {}

    @PostMapping(
            value = "/tts",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> tts(@RequestBody TtsRequest req) throws IOException {
        String text = req.text() == null ? "" : req.text();
        String voice = (req.voice() == null || req.voice().isBlank()) ? "ALLOY" : req.voice();
        float speed = req.speed() == null ? 1.0f : req.speed();

        OpenAiAudioApi.SpeechRequest.Voice voiceEnum;
        try { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.valueOf(voice.toUpperCase()); }
        catch (Exception e) { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.ALLOY; }

        var opts = OpenAiAudioSpeechOptions.builder()
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .voice(voiceEnum)
                .speed(speed)
                .build();

        var speech = ttsModel.call(new SpeechPrompt(text, opts));
        byte[] mp3 = speech.getResult().getOutput();

        return ResponseEntity.ok(Map.of(
                "audioBase64", Base64.getEncoder().encodeToString(mp3)
        ));
    }
}
