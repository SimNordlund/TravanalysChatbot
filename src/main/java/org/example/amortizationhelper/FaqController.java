package org.example.amortizationhelper;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class FaqController {
  @Value("classpath:/prompts/rag-prompt-template.st")
  private Resource ragPromptTemplate;

  private final ChatClient chatClient;

  public FaqController(ChatClient.Builder builder, VectorStore vectorStore) {
    this.chatClient = builder
        .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore,SearchRequest.builder()
            .build()))
        .defaultSystem("You are a helpful bank-robot-assistant. Du är en expert på svenska finansföreskrifter. Always start with saying Yo Simon and tell me the first sentence of the document / pdf"
            + "\n ")
        .build();

  }

  @GetMapping("/faq")
  public String faq(@RequestParam(value = "message", defaultValue = "If no message was provided scream BEJSKORW") String message) {

    return chatClient.prompt()
        .user(u -> u.text(ragPromptTemplate).param("input", message))
        .call()
        .content();
  }
}
