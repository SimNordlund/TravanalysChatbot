package org.example.amortizationhelper.Controller;

import java.util.List;
import java.util.stream.Collectors;
import org.example.amortizationhelper.Entity.AmorteringsUnderlag;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Just used for testing StructuredOutput.
 * Can be ignored.
 */

@RestController
public class StructuredOutputController {
  @Value("classpath:/prompts/strucuredOutputPrompt.st")
  private Resource ragPromptTemplate;

  private final ChatClient chatClient;

  public StructuredOutputController(ChatClient.Builder builder) {
    this.chatClient = builder
        .defaultSystem("You are a helpful banking assistant specializing in Swedish financial regulations. " +
            "Answer questions about amortization requirements clearly and concisely. " +
            "Always respond in Swedish. ")
        .build();
  }

  @GetMapping("/structuredData")
  public AmorteringsUnderlag structuredOutput(@RequestParam(value = "message", defaultValue = "") String message) {
      String pdfContent = extractPdfContent();

      return chatClient.prompt()
          .system(ragPromptTemplate)
          .user(pdfContent)
          .call()
          .entity(AmorteringsUnderlag.class);
  }

  private String extractPdfContent() {
    FileSystemResource pdfResource = new FileSystemResource("src/main/resources/docs/AU123.pdf");
    PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
    List<Document> documents = pdfReader.get();

    return documents.stream()
        .map(Document::getText)
        .collect(Collectors.joining("\n\n"));
  }
}
