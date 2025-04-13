package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Entity.AmorteringsUnderlag;
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
public class StructuredOutputController {
  @Value("classpath:/prompts/rag-prompt-template.st")
  private Resource ragPromptTemplate;

  private final ChatClient chatClient;

  public StructuredOutputController(ChatClient.Builder builder, VectorStore vectorStore) {
    this.chatClient = builder
        .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore,SearchRequest.builder()
            .build()))
        .defaultSystem("You are a helpful bank-robot-assistant. Du är en expert på svenska finansföreskrifter. Always start with saying Yo Simon and tell me the first sentence of the document / pdf"
            + "Give me 10 lines of the document and create a object of Amorteringsunderlag with the first line of the document as mortgageObject and the rest of the 10 lines as amortizationValues. "
            + "\n ")
        .build();

  }

  @GetMapping("/faq")
  public AmorteringsUnderlag faq(@RequestParam(value = "message", defaultValue = "If no message was provided scream kekw") String message) {

    return chatClient.prompt()
        .user(u -> u.text(ragPromptTemplate).param("input", message))
        .call()
        .entity(AmorteringsUnderlag.class);
  }
}
