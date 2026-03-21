package org.example.amortizationhelper.config;

import lombok.RequiredArgsConstructor;
import org.example.amortizationhelper.Email.EmailTools;
import org.example.amortizationhelper.Tools.KopAndelTools;
import org.example.amortizationhelper.Tools.StartlistaTools;
import org.example.amortizationhelper.Tools.TravTools;
import org.example.amortizationhelper.WebSearch.WebSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class AiChatConfig {

    @Bean
    public ChatClient chatCliente(ChatClient.Builder builder,
                                  VectorStore vectorStore,
                                  ResourceLoader resourceLoader,
                                  TravTools travTools,
                                  StartlistaTools startlistaTools,
                                  EmailTools emailTools,
                                  KopAndelTools kopAndelTools,
                                  //RoiTools roiTools,
                                  WebSearchTools webSearchTools) throws Exception {

        var retriever = VectorStoreDocumentRetriever.builder()
                //.similarityThreshold(0.78) hiss or kiss?
                //.topK(4)
                .vectorStore(vectorStore)
                .build();

        Resource promptRes = resourceLoader.getResource("classpath:/prompts/travPrompt.st");
        String templateString;
        try (var in = promptRes.getInputStream()) {
            templateString = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }

        var template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template(templateString)
                .build();

        var queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .promptTemplate(template)
                .build();

        var ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter)
                .build();

        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(12)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();

        return builder
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, webSearchTools, emailTools, kopAndelTools) //roiTools temp removed 2026-03-14
                .build();
    }
}
