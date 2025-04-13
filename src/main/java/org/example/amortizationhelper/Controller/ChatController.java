package org.example.amortizationhelper.Controller;

import java.io.IOException;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.Loader;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  private final ResourceLoader resourceLoader;
  private final ChatClient chatClient;

  private String lastCustomerName = "";
  private String lastObjectName = "";
  private String lastUploadedContent = "";

  public ChatController(ChatClient.Builder builder, VectorStore vectorStore, ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
      Resource promptResource = resourceLoader.getResource("classpath:/prompts/amortergsunderlagPrompt.st");

      this.chatClient = builder
          .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
              .build()))
          .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
          .defaultSystem(promptResource)
          .build();
  }

  @GetMapping("/chat-stream")
  public Flux<String> chatStream(@RequestParam(value = "message") String message) {
    String userMessage = message;
    if (!lastUploadedContent.isEmpty()) {
      userMessage += "\n\nReference the following previously uploaded content:\n" + lastUploadedContent;
    }

    String chatSystemPrompt = "You are a helpful banking assistant specializing in Swedish financial regulations. " +
        "Answer questions about amortization requirements clearly and concisely. " +
        "Always respond in Swedish and refer to the document content when possible."
        + "You can also refer to the information that was provided via the vector database, the so called Ett skärpt amorteringskrav för hushåll med höga skuldkvoter"
        + "Never apologize for your answers and never say that you are a AI model. "
        + "Try to use no more than 300 tokens in your answers";


    return chatClient.prompt()
        .system(chatSystemPrompt)
        .user(userMessage)
        .stream()
        .content()
        .map(this::restoreCustomerName);
  }

  @PostMapping("/upload")
  public String handleFileUpload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "instructions", defaultValue = "Analyze this content") String instructions) {
    try {
      String content;
      String filename = file.getOriginalFilename();

      if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
        try (PDDocument document = Loader.loadPDF(file.getInputStream().readAllBytes())) {
          PDFTextStripper stripper = new PDFTextStripper();
          content = stripper.getText(document);

          content = redactCustomerNames(content);
        }
      } else {
        content = new String(file.getBytes());
        content = redactCustomerNames(content);
      }

      this.lastUploadedContent = content;

      String llmResponse = chatClient.prompt()
          .user(instructions + "\n\nContent:\n" + content)
          .call()
          .content();

      return restoreCustomerName(llmResponse);
    } catch (IOException e) {
      return "Error processing file: " + e.getMessage();
    }
  }

  @GetMapping("/chat")
  public String chat(@RequestParam(value = "message") String message) {
    String userMessage = message;
    if (!lastUploadedContent.isEmpty()) {
      userMessage += "\n\n Use a maximum of 100 tokens in your answers and" +
          "reference the following previously uploaded content:\n" + lastUploadedContent;
    }

    String chatSystemPrompt = "You are a helpful banking assistant specializing in Swedish financial regulations. " +
        "Answer questions about amortization requirements clearly and concisely. " +
        "Always respond in Swedish and refer to the document content when possible."
        + "Use maximum 100 tokens or 100 words in each answer"
        + "Start each sentence with HOLA";

    return chatClient.prompt()
        .system(chatSystemPrompt)
        .user(userMessage)
        .call()
        .content();
  }

  @GetMapping("/advice")
  public String advice1337(
      @RequestParam(value = "message", defaultValue = "Tell me 10 facts about amortization in Sweden. In the swedish language") String message) {
    return chatClient.prompt()
        .user(message)
        .call()
        .content();
  }

  private String redactCustomerNames(String content) {
    String customerPattern = "(Kund \\[1\\]|Kundi | Kund)(.*?)(?=\\r?\\n|$)";
    java.util.regex.Matcher customerMatcher = java.util.regex.Pattern.compile(customerPattern).matcher(content);
    if (customerMatcher.find()) {
      this.lastCustomerName = customerMatcher.group(2).trim();
      content = content.replaceAll(customerPattern, "$1 [Exempel Kund]");
    }

    String objectPattern = "(Objekt)(.*?)(?=\\r?\\n|$)";
    java.util.regex.Matcher objectMatcher = java.util.regex.Pattern.compile(objectPattern).matcher(content);
    if (objectMatcher.find()) {
      this.lastObjectName = objectMatcher.group(2).trim();
      content = content.replaceAll(objectPattern, "$1 [Exempel Objekt]");
    }

    log.info("Information from AU sent to the LLM: {}", content);
    return content;
  }

  private String restoreCustomerName(String llmResponse) {
    String result = llmResponse;

    if (this.lastCustomerName != null && !this.lastCustomerName.isEmpty()) {
      result = result.replace("Exempel Kund", this.lastCustomerName);
    }

    if (this.lastObjectName != null && !this.lastObjectName.isEmpty()) {
      result = result.replace("Exempel Objekt", this.lastObjectName);
    }

    return result;
  }
}
