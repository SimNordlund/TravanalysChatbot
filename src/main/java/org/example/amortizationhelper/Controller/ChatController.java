package org.example.amortizationhelper.Controller;

import java.io.IOException;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.core.Ordered;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.Loader;
import reactor.core.publisher.Flux;

/**
 * IMPORTANT NOTE: You must add your own OpenAI API key or Bedrock key in the application.properties file
 * for the embedding model to work. Without a valid key, the vector database creation will fail and the application won't function correctly.

 * ChatController handles chat interactions and file uploads.
 * It uses a chat client to process user messages
 * and provides responses based on the uploaded content.
 * It also redacts sensitive information
 * from the uploaded content before processing
 * and restores the original names in the response.
 */

@RestController
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  private final ResourceLoader resourceLoader;
  private final ChatClient chatClient;

  private String lastCustomerName = "";
  private String lastObjectName = "";
  private String lastUploadedContent = "";

  public ChatController(ChatClient.Builder builder,
                        VectorStore vectorStore,
                        ResourceLoader resourceLoader) {

    this.resourceLoader = resourceLoader;
    Resource promptResource = resourceLoader.getResource("classpath:/prompts/travPrompt.st");

    /* 1️⃣  Bygg retrievern */
    var documentRetriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .topK(4)
            .similarityThreshold(0.3)        //Changed! – mjukare tröskel
            .build();

    /* 2️⃣  RAG-rådgivare, men ge den lägre prioritet */
    var ragAdvisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(documentRetriever)
            //.order(Ordered.HIGHEST_PRECEDENCE) //Changed! – lägre än system-prompten
            .build();

    /* 3️⃣  ChatClient */

    ChatMemory memory = MessageWindowChatMemory.builder()
            .maxMessages(3)   // only the most recent 12 messages are retained  //Changed!
            .build();

// 2️⃣ Build your MessageChatMemoryAdvisor with that memory
    var memoryAdvisor = MessageChatMemoryAdvisor
            .builder(memory)  // supply the windowed ChatMemory here
            .build();

// 3️⃣ Register it on your ChatClient
    this.chatClient = builder
            .defaultAdvisors(ragAdvisor, memoryAdvisor)
            .defaultSystem(promptResource)
            .build();
  }

  @GetMapping("/chat-stream")
  public Flux<String> chatStream(@RequestParam("message") String message) {

    //Resource promptResource = resourceLoader.getResource("classpath:/prompts/travPrompt.st");

    String userMessage = message.replaceAll("\\p{C}", "");

    return chatClient.prompt()
            //.system(promptResource)
            .user(userMessage)
            .stream()
            .content();
  }

 /* @GetMapping("/chat-stream")
  public Flux<String> chatStream(@RequestParam(value = "message") String message) {

    Resource promptResource = resourceLoader.getResource("classpath:/prompts/travPrompt.st");

    String userMessage = message;
    if (!lastUploadedContent.isEmpty()) {
      userMessage += "\n\nReference the following previously uploaded content:\n" + lastUploadedContent;
    }

    return chatClient.prompt()
        .system(promptResource)
        .user(userMessage)
        .stream()
        .content()
        .map(this::restoreCustomerName);
  } */

  /* @PostMapping("/upload")
  public String handleFileUpload(
      @RequestParam("file") MultipartFile file){
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
          .user("Analyze this content \n\nContent:\n" + content)
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

    String chatSystemPrompt = "You speak swedish";

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
    String customerPattern = "(Kund \\[1]|Kundi | Kund)(.*?)(?=\\r?\\n|$)";
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
  } */
}
