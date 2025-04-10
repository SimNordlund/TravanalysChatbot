package org.example.amortizationhelper;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

  String testString = "You are financial advisor helper. You also answer questions regarding the documents that is provided as amorteringsunderlag and the document you got via the vector database. You answer in SWEDISH language.\n"
      + "Based on the Document that is provided to you via the vector database and the pdf which is a amorteringsunderlag that I sent you I want you to make a special sentence once you figured which kind of of amortization model the amorteringsunderlag is showing. I will give you some rules below:\n"
      + "\n"
      + "1. When looking at Låneinformation om objekt and the values between points 1-4. If a customer only has a value under point 3. Then you should write \"FI-03: Huvudregeln.\"\n"
      + "2. When looking at Låneinformation om objekt and the values between points 1-4. If a customer only has a value under point 2. Then you should write \"FI-02: Huvudregeln.\"\n"
      + "3. When looking at Låneinformation om objekt and the values between points 1-4.If a customer only has a value under point 1. Then you should write \"Gamla krav: Omfattas ej.\"\n"
      + "\n"
      + "4. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1 and 2 and the same customer also has a value under point 4. Then you should write \"FI-02: Alternativregeln.\"\n"
      + "5. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 2 and 3 and the same customer also has a value under point 4. Then you should write \"FI-03: Alternativregeln.\"\n"
      + "\n"
      + "6. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1, 2, 3 and the same customer also has a value under point 4. Then you should write \"FI-03: Alternativregeln.\"\n"
      + "7. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1, 2, 3 and the same customer does not have a value under point 4. Then you should write \"FI-03: Huvudregeln.\"\n"
      + "\n"
      + "8. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1 and 2 and the same customer does not have a value under point 4. Then you should write \"FI-02: Huvudregeln.\"\n"
      + "9. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1 and 3 and the same customer also has a value under point 4. Then you should write \"Gamla krav: Alternativregeln.\"\n"
      + "10. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1 and 3 and the same customer does not have a value under point 4. Then you should write \"FI-03: Huvudregeln.\"\n"
      + "11. When looking at Låneinformation om objekt and the values between points 1-4. If a customer has a value under point 1 and 2 and the same customer does have a value under point 4. Then you should write \"Gamla krav: Alternativregeln.\"\n"
      + "\n"
      + "12. If the amorteringsunderlag does not fit anyone of these above. Please let me know that.\n"
      + "13. If you know the answer always first give me the model followed by the customers name, for example: FI-03, Huvudregeln then newline and afterwards Kund: Customersname/names and after that new line and Objekt: The name of the apartment/villa/objekt. After that you make a new sentance and follow rule 14.\n"
      + "14. Always explain your though process when coming up with which models the customer has.\n"
      + "15. Skuldkvot is not needed to determine the amortization model, check the rules between 1-11 for guidance.\n"
      + "15. Nämn inget om reglerna du fått av mig i ditt svar och ställ inga ytterligare frågor till mig efter du angivit amorteringsmodell men i meddelanden efter går det bra att fråga. Your answer should not be longer than 320 tokens";

  private String lastCustomerName = "";

  private String lastObjectName = "";
  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  private final ChatClient chatClient;

  private String lastUploadedContent = "";

  public ChatController(ChatClient.Builder builder, VectorStore vectorStore) {
    this.chatClient = builder
        .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
            .build()))
        .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
        .defaultSystem(testString)
        .build();
  }

  @GetMapping("/advice")
  public String advice1337(
      @RequestParam(value = "message", defaultValue = "Tell me 10 facts about amortization in Sweden. In the swedish language") String message) {
    return chatClient.prompt()
        .user(message)
        .call()
        .content();
  }

  @GetMapping("/chat")
  public String chat(@RequestParam(value = "message") String message) {
    // Include the last uploaded content if available
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


  @GetMapping("/chat-stream")
  public Flux<String> chatStream(@RequestParam(value = "message") String message) {
    // Include the last uploaded content if available
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

      // Handle PDFs specifically
      if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
        try (PDDocument document = Loader.loadPDF(file.getInputStream().readAllBytes())) {
          PDFTextStripper stripper = new PDFTextStripper();
          content = stripper.getText(document);

          // Redact customer names
          content = redactCustomerNames(content);
        }
      } else {
        // For non-PDF files, use the existing text approach
        content = new String(file.getBytes());
        content = redactCustomerNames(content);
      }

      // Store the content for future chat references
      this.lastUploadedContent = content;

      String llmResponse = chatClient.prompt()
          .user(instructions + "\n\nContent:\n" + content)
          .call()
          .content();

      // Restore customer name in the response
      return restoreCustomerName(llmResponse);
    } catch (IOException e) {
      return "Error processing file: " + e.getMessage();
    }
  }

  private String redactCustomerNames(String content) {
    // Redact customer name
    String customerPattern = "(Kund \\[1\\]|Kundi:)(.*?)(?=\\r?\\n|$)";
    java.util.regex.Matcher customerMatcher = java.util.regex.Pattern.compile(customerPattern).matcher(content);
    if (customerMatcher.find()) {
      this.lastCustomerName = customerMatcher.group(2).trim();
      content = content.replaceAll(customerPattern, "$1 [Bo Loansen]");
    }

    // Redact object name
    String objectPattern = "(Objekt)(.*?)(?=\\r?\\n|$)";
    java.util.regex.Matcher objectMatcher = java.util.regex.Pattern.compile(objectPattern).matcher(content);
    if (objectMatcher.find()) {
      this.lastObjectName = objectMatcher.group(2).trim();
      content = content.replaceAll(objectPattern, "$1 [Brf Belåningsgrad lgh 1337]");
    }

    log.info("Ghosted: {}", content);
    return content;
  }

  private String restoreCustomerName(String llmResponse) {
    String result = llmResponse;

    // Restore customer name
    if (this.lastCustomerName != null && !this.lastCustomerName.isEmpty()) {
      result = result.replace("Bo Loansen", this.lastCustomerName);
    }

    // Restore object name
    if (this.lastObjectName != null && !this.lastObjectName.isEmpty()) {
      result = result.replace("Brf Belåningsgrad lgh 1337", this.lastObjectName);
    }

    return result;
  }
}
