// package justera efter ditt projekt
package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Tools.RoiTools;                            //Changed!
import org.example.amortizationhelper.Tools.StartlistaTools;                     //Changed!
import org.example.amortizationhelper.Tools.TravTools;                           //Changed!

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;      //Changed!
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;              //Changed!
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;            //Changed!

import org.springframework.ai.openai.OpenAiAudioSpeechModel;                      //Changed!
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;                   //Changed!
import org.springframework.ai.openai.api.OpenAiAudioApi;                         //Changed!
import org.springframework.ai.openai.audio.speech.SpeechPrompt;                  //Changed!

import org.springframework.ai.chat.client.ChatClient;                            //Changed!
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;      //Changed!
import org.springframework.ai.chat.memory.ChatMemory;                            //Changed!
import org.springframework.ai.chat.memory.MessageWindowChatMemory;               //Changed!
import org.springframework.ai.chat.prompt.PromptTemplate;                        //Changed!
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;          //Changed!
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter; //Changed!
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever; //Changed!
import org.springframework.ai.template.st.StTemplateRenderer;                     //Changed!
import org.springframework.ai.vectorstore.VectorStore;                           //Changed!

import org.springframework.beans.factory.annotation.Autowired;                   //Changed!
import org.springframework.core.io.FileSystemResource;                           //Changed!
import org.springframework.core.io.Resource;                                      //Changed!
import org.springframework.core.io.ResourceLoader;                                //Changed!

import org.springframework.http.MediaType;                                       //Changed!
import org.springframework.http.ResponseEntity;                                   //Changed!
import org.springframework.util.StreamUtils;                                     //Changed!
import org.springframework.web.bind.annotation.*;                                 //Changed!
import org.springframework.web.multipart.MultipartFile;                           //Changed!

import java.io.IOException;                                                      //Changed!
import java.nio.charset.StandardCharsets;                                        //Changed!
import java.nio.file.Files;                                                      //Changed!
import java.nio.file.Path;                                                       //Changed!
import java.util.Base64;                                                         //Changed!
import java.util.Map;                                                            //Changed!

@RestController                                                                   //Changed!
@RequestMapping("/voice")                                                         //Changed!
@CrossOrigin(origins = "*") // lås ner i prod                                     //Changed!
public class VoiceController {                                                    //Changed!

    private final OpenAiAudioTranscriptionModel sttModel;                        //Changed!
    private final OpenAiAudioSpeechModel ttsModel;                               //Changed!
    private final ChatClient chatClient;                                         //Changed!

    @Autowired                                                                    //Changed!
    public VoiceController(OpenAiAudioTranscriptionModel sttModel,               //Changed!
                           OpenAiAudioSpeechModel ttsModel,                      //Changed!
                           ChatClient.Builder builder,                           //Changed!
                           VectorStore vectorStore,                              //Changed!
                           ResourceLoader resourceLoader,                        //Changed!
                           TravTools travTools,                                  //Changed!
                           StartlistaTools startlistaTools,                      //Changed!
                           RoiTools roiTools) throws Exception {                 //Changed!
        this.sttModel = sttModel;                                                //Changed!
        this.ttsModel = ttsModel;                                                //Changed!

        // Bygg samma RAG+minne+tools som i ChatController                      //Changed!
        var retriever = VectorStoreDocumentRetriever.builder()                   //Changed!
                .vectorStore(vectorStore)
                .build();

        Resource promptRes = resourceLoader.getResource("classpath:/prompts/travPrompt.st"); //Changed!
        String templateString;
        try (var in = promptRes.getInputStream()) {
            templateString = StreamUtils.copyToString(in, StandardCharsets.UTF_8); //Changed!
        }

        PromptTemplate template = PromptTemplate.builder()                       //Changed!
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template(templateString)
                .build();

        var queryAugmenter = ContextualQueryAugmenter.builder()                  //Changed!
                .allowEmptyContext(true)
                .promptTemplate(template)
                .build();

        var ragAdvisor = RetrievalAugmentationAdvisor.builder()                  //Changed!
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter)
                .build();

        ChatMemory memory = MessageWindowChatMemory.builder()                    //Changed!
                .maxMessages(12)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();    //Changed!

        this.chatClient = builder                                               //Changed!
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, roiTools)
                .build();
    }

    // === A) FULL pipeline: STT -> Chat (RAG) -> TTS (en fil) =================
    @PostMapping(
            value = "/chat",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    ) //Changed!
    public ResponseEntity<Map<String, Object>> chatWithAudio(                   //Changed!
                                                                                @RequestPart("file") MultipartFile file,
                                                                                @RequestParam(name = "voice", defaultValue = "ALLOY") String voiceName, //Changed!
                                                                                @RequestParam(name = "speed", defaultValue = "1.0") float speed         //Changed!
    ) throws IOException {

        String original = file.getOriginalFilename();                           //Changed!
        String ext = (original != null && original.contains("."))               //Changed!
                ? original.substring(original.lastIndexOf('.'))
                : ".webm";
        Path tmp = Files.createTempFile("voice-", ext);                         //Changed!
        file.transferTo(tmp);                                                   //Changed!

        try {
            // 1) STT
            var trOpts = OpenAiAudioTranscriptionOptions.builder()              //Changed!
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                    .language("sv")
                    .temperature(0f)
                    .build();

            var trPrompt = new AudioTranscriptionPrompt(new FileSystemResource(tmp.toFile()), trOpts); //Changed!
            var trResp = sttModel.call(trPrompt);
            String userText = trResp.getResult().getOutput();                   //Changed!

            // 2) Chat (lägg gärna ett kort röstläge i system om du vill)
            String answerText = chatClient.prompt()                             //Changed!
                    .system("""
                        Du är i röstläge. Svara kort, utan markdown, på tydlig svenska.
                        Korta meningar. Max tre punkter i listor. Säg "procent" istället för % om uttalet blir otydligt.
                    """) //Changed!
                    .user(userText == null ? "" : userText)
                    .call()
                    .content();

            // 3) TTS (MP3)
            OpenAiAudioApi.SpeechRequest.Voice voiceEnum;                       //Changed!
            try { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.valueOf(voiceName.toUpperCase()); }
            catch (Exception e) { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.ALLOY; }

            var speechOpts = OpenAiAudioSpeechOptions.builder()                 //Changed!
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .voice(voiceEnum)
                    .speed(speed)
                    .build();

            var speechResp = ttsModel.call(new SpeechPrompt(answerText, speechOpts)); //Changed!
            byte[] mp3 = speechResp.getResult().getOutput();                    //Changed!

            return ResponseEntity.ok(Map.of(                                     //Changed!
                    "text", answerText,
                    "audioBase64", Base64.getEncoder().encodeToString(mp3)
            ));
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}     //Changed!
        }
    }

    // === B) STT-only: multipart -> { text } ===================================
    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    ) //Changed!
    public ResponseEntity<Map<String, Object>> transcribe(                      //Changed!
                                                                                @RequestPart("file") MultipartFile file) throws IOException {

        String original = file.getOriginalFilename();                           //Changed!
        String ext = (original != null && original.contains("."))               //Changed!
                ? original.substring(original.lastIndexOf('.'))
                : ".webm";
        Path tmp = Files.createTempFile("stt-", ext);                           //Changed!
        file.transferTo(tmp.toFile());                                          //Changed!

        try {
            var trOpts = OpenAiAudioTranscriptionOptions.builder()              //Changed!
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                    .language("sv")
                    .temperature(0f)
                    .build();

            var prompt = new AudioTranscriptionPrompt(new FileSystemResource(tmp.toFile()), trOpts); //Changed!
            var resp = sttModel.call(prompt);                                   //Changed!
            String text = resp.getResult().getOutput();                         //Changed!

            return ResponseEntity.ok(Map.of("text", text == null ? "" : text)); //Changed!
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}     //Changed!
        }
    }

    // === C) TTS-only: { text, voice?, speed? } -> { audioBase64 } =============
    public static record TtsRequest(String text, String voice, Float speed) {}  //Changed!

    @PostMapping(
            value = "/tts",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    ) //Changed!
    public ResponseEntity<Map<String, Object>> tts(@RequestBody TtsRequest req) throws IOException { //Changed!
        String text = req.text() == null ? "" : req.text();                     //Changed!
        String voice = (req.voice() == null || req.voice().isBlank()) ? "ALLOY" : req.voice(); //Changed!
        float speed = req.speed() == null ? 1.0f : req.speed();                 //Changed!

        OpenAiAudioApi.SpeechRequest.Voice voiceEnum;                           //Changed!
        try { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.valueOf(voice.toUpperCase()); }
        catch (Exception e) { voiceEnum = OpenAiAudioApi.SpeechRequest.Voice.ALLOY; }

        var opts = OpenAiAudioSpeechOptions.builder()                           //Changed!
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .voice(voiceEnum)
                .speed(speed)
                .build();

        var speech = ttsModel.call(new SpeechPrompt(text, opts));               //Changed!
        byte[] mp3 = speech.getResult().getOutput();                            //Changed!

        return ResponseEntity.ok(Map.of(                                        //Changed!
                "audioBase64", Base64.getEncoder().encodeToString(mp3)
        ));
    }
}
