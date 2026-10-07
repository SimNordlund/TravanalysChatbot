package org.example.amortizationhelper.RagConfig;

import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Configuration
public class RagConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

  @Value("${vectorstore.filepath:temp/vectorstore.json}")
  private String vectorStoreFilePath;

  @Value("classpath:/docs/rankdaynine3.pdf")
  private Resource pdfResource;

  @Bean
  public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
    SimpleVectorStore simpleVectorStore = new OptionalDocumentVectorStore(embeddingModel);

    File vectorStoreFile = new File(vectorStoreFilePath).getAbsoluteFile();

    try {
      if (vectorStoreFile.exists()) {
        log.info("Vector store file found. Loading existing embeddings...");
        simpleVectorStore.load(vectorStoreFile);
      } else {
        log.info("Vector store file not found. Creating from PDF...");

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
        List<Document> documents = pdfReader.get();
        for (Document doc : documents) {
          doc.getMetadata().put("filename", pdfResource.getFilename());
          doc.getMetadata().put("source_type", "background_document");
        }

        var splitter = new TokenTextSplitter(512, 64, 20, 0, false);
        List<Document> splitDocuments = splitter.apply(documents);
        simpleVectorStore.add(splitDocuments);
        try {
          Files.createDirectories(vectorStoreFile.toPath().getParent());
          simpleVectorStore.save(vectorStoreFile);
          log.info("Vector store created and saved to {}", vectorStoreFile.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
          // The in-memory documents are still usable when the cache cannot be persisted.
          log.warn("Could not save vector store ({}); using in-memory documents.", e.getClass().getSimpleName());
        }
      }
    } catch (RuntimeException e) {
      // Site help and database tools should also work during an embeddings outage or with a bad cache.
      log.warn("PDF knowledge unavailable ({}); starting with site guide and database tools.",
              e.getClass().getSimpleName());
      return new OptionalDocumentVectorStore(embeddingModel);
    }
    return simpleVectorStore;
  }

  private static final class OptionalDocumentVectorStore extends SimpleVectorStore {
    private OptionalDocumentVectorStore(EmbeddingModel embeddingModel) {
      super(SimpleVectorStore.builder(embeddingModel));
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
      // SimpleVectorStore otherwise requests an embedding even when there are no documents.
      return store.isEmpty() ? List.of() : super.doSimilaritySearch(request);
    }
  }
}
