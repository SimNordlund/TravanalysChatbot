package org.example.amortizationhelper.RagConfig;

import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * RagConfiguration sets up a RAG system using a Vector Store.
 * It loads documents from a PDF and saves the embeddings for fast retrieval.
 *
 * Note: You must set a valid OpenAI or Bedrock API key for this to work.
 */
@Configuration
public class RagConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

  @Value("${vectorstore.filepath:temp/vectorstore.json}")
  private String vectorStoreFilePath; // Default to temp/ for Windows/dev

  @Value("classpath:/docs/rankdaysix.pdf")
  private Resource pdfResource;

  @Bean
  public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
    SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel)
            .build();

    File vectorStoreFile = new File(vectorStoreFilePath);

    // Ensure parent directory exists
    if (!vectorStoreFile.getParentFile().exists()) {
      boolean dirsCreated = vectorStoreFile.getParentFile().mkdirs();
      if (dirsCreated) {
        log.info("Created directory for vectorstore: {}", vectorStoreFile.getParentFile().getAbsolutePath());
      }
    }

    if (vectorStoreFile.exists()) {
      log.info("Vector store file found. Loading existing embeddings...");
      simpleVectorStore.load(vectorStoreFile);
    } else {
      log.info("🚀 Vector store file not found. Creating from PDF...");

      PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
      List<Document> documents = pdfReader.get();

      for (Document doc : documents) {
        doc.getMetadata().put("filename", "travpro.pdf");
      }

      var splitter = new TokenTextSplitter(512, 64, 20, 0, false);
      List<Document> splitDocuments = splitter.apply(documents);

      simpleVectorStore.add(splitDocuments);
      simpleVectorStore.save(vectorStoreFile);

      log.info("Vector store created and saved to {}", vectorStoreFile.getAbsolutePath());
    }
    return simpleVectorStore;
  }
}
