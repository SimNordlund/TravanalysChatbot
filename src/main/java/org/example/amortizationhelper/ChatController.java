package org.example.amortizationhelper;

import java.io.IOException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.Loader;

@RestController
public class ChatController {

  private final ChatClient chatClient;

  public ChatController(ChatClient.Builder builder) {
    this.chatClient = builder
        .defaultSystem("You are a helpful bank-robot-assistant.")
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


  @PostMapping("/upload")
  public String handleFileUpload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "instructions", defaultValue = "Analyze this content") String instructions) {
    try {
      if (file.isEmpty()) {
        return "Please upload a non-empty file";
      }

      if (file.getSize() > 5 * 1024 * 1024) {
        return "File size exceeds the limit (5MB)";
      }

      String content;
      String filename = file.getOriginalFilename();

      // Handle PDFs specifically
      if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
        try (PDDocument document = Loader.loadPDF(file.getInputStream().readAllBytes())) { // use Loader.loadPDF
          PDFTextStripper stripper = new PDFTextStripper();
          content = stripper.getText(document);
        }
      } else {
        // For non-PDF files, use the existing text approach
        content = new String(file.getBytes());
      }

      return chatClient.prompt()
          .user(instructions + "\n\nContent:\n" + content)
          .call()
          .content();
    } catch (IOException e) {
      return "Error processing file: " + e.getMessage();
    }
  }
}
