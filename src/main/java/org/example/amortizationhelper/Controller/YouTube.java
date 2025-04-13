package org.example.amortizationhelper.Controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class YouTube {

  private final ChatClient chatClient;
  @Value("classpath:/prompts/amortergsunderlagPrompt.st")
  private Resource ytPromptResource;

  public YouTube(ChatClient.Builder builder, VectorStore vectorStore) {
    this.chatClient = builder
        .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
            .build()))
       // .defaultSystem("You are a helpful bank-robot-assistant. You start each sentence with KEKW") Behöver ej denna om amortergsunderlagPrompt.st fungerar
        .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
        .build();
  }

  @GetMapping("/popular-resource")
  public String findPopularYouTubers(@RequestParam(value = "genre", defaultValue = "tech") String genre) {
    return chatClient.prompt()
        .user(u -> u.text(ytPromptResource).param("genre",genre))
        .call()
        .content();
  }

  @GetMapping("/popular")
  public String findPopularYouTubersStepOne(@RequestParam(value = "genre", defaultValue = "tech") String genre) {
    String message = """
            List 10 of the most popular YouTubers in {genre} along with their current subscriber counts. If you don't know
            the answer , just say "I don't know".
            """;

    return chatClient.prompt()
        .user(u -> u.text(message).param("genre",genre))
        .call()
        .content();
  }
}
