package org.example.amortizationhelper.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

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

import org.example.amortizationhelper.Tools.TravTools;
import org.example.amortizationhelper.Tools.StartlistaTools;
import org.example.amortizationhelper.Tools.RoiTools;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class AiChatConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 VectorStore vectorStore,
                                 ResourceLoader resourceLoader,
                                 TravTools travTools,
                                 StartlistaTools startlistaTools,
                                 RoiTools roiTools) throws Exception {

        var retriever = VectorStoreDocumentRetriever.builder()
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
                .defaultTools(travTools, startlistaTools, roiTools)
                .build();
    }
}
