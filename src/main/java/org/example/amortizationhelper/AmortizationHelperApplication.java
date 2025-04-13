package org.example.amortizationhelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AmortizationHelperApplication {

  /**
   * INSTRUCTIONS IN ChatController
   * IMPORTANT NOTE: You must add your own OpenAI API key or Bedrock key in the application.properties file
   * for the embedding model to work. Without a valid key, the vector database creation will fail and the application won't function correctly.
   */

  public static void main(String[] args) {
    SpringApplication.run(AmortizationHelperApplication.class, args);
  }
}
