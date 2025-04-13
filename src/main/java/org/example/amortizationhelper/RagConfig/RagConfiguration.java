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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * RagConfiguration is a Spring configuration class
 * that sets up the RAG (Retrieval-Augmented Generation) system.
 * It initializes a SimpleVectorStore
 * and loads documents from a PDF file.
 * The documents are split into smaller chunks
 * using a TextSplitter
 * and stored in the vector store.
 * If the vector store file already exists,
 * it loads the existing data
 * instead of reprocessing the documents.
 * The class uses a PagePdfDocumentReader
 * to read the PDF file
 * and extract the text content.
 * The vector store file is saved
 * in the resources/data directory.

 *  IMPORTANT NOTE: You must add your own OpenAI API key or Bedrock key in the application.properties file
 *  for the embedding model to work. Without a valid key, the vector database creation will fail and the application won't function correctly.
 */

@Configuration
public class RagConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

  @Value("vectorstore.json")
  private String vectorStoreName;

  @Value("classpath:/docs/FI-REGLER.pdf")
  private Resource pdfResource;

  @Bean
  SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
    var simpleVectorStore = SimpleVectorStore.builder(embeddingModel)
        .build();

    var vectorStoreFile = getVectorStoreFile();
    if (vectorStoreFile.exists()) {
      log.info("Vector Store File Exists,");
      simpleVectorStore.load(vectorStoreFile);
    } else {
      log.info("Vector Store File Does Not Exist, loading documents");

      PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
      List<Document> documents = pdfReader.get();

      for (Document doc : documents) {
        doc.getMetadata().put("filename", "Finansinspektionen.pdf");
      }

      TextSplitter textSplitter = new TokenTextSplitter();
      List<Document> splitDocuments = textSplitter.apply(documents);
      simpleVectorStore.add(splitDocuments);
      simpleVectorStore.save(vectorStoreFile);
    }
    return simpleVectorStore;
  }

  private File getVectorStoreFile() {
    Path path = Paths.get("src", "main", "resources", "data");
    String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName;
    return new File(absolutePath);
  }

}
