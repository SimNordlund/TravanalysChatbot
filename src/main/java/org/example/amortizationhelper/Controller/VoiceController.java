package org.example.amortizationhelper.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/voice")
public class VoiceController {

    private final OpenAiAudioTranscriptionModel sttModel;
    private final OpenAiAudioSpeechModel ttsModel;
    private final ChatClient.Builder chatClientBuilder;

    @PostMapping(
            value = "/chat",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> chatWithAudio(
            @RequestPart("file") MultipartFile file) throws IOException {

        // 1) STT – transkribera ljudfilen
        var trOpts = OpenAiAudioTranscriptionOptions.builder()
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .language("sv")
                .temperature(0f)
                .build();

        var trPrompt = new AudioTranscriptionPrompt(file.getResource(), trOpts);
        var trResp = sttModel.call(trPrompt);
        String userText = trResp.getResult().getOutput();

        // 2) Chat – enklare med ChatClient
        String answerText = chatClientBuilder.build()
                .prompt()
                .user(userText == null ? "" : userText)
                .call()
                .content();

        // 3) TTS – generera MP3 av svaret
        var speechOpts = OpenAiAudioSpeechOptions.builder()
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                .build();

        var speechResp = ttsModel.call(new SpeechPrompt(answerText, speechOpts));
        byte[] mp3 = speechResp.getResult().getOutput();

        return ResponseEntity.ok(Map.of(
                "text", answerText,
                "audioBase64", Base64.getEncoder().encodeToString(mp3)
        ));
    }
}
