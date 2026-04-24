package org.example.amortizationhelper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import org.example.amortizationhelper.chat.ExpiringInMemoryChatMemory;
import org.example.amortizationhelper.Email.EmailTools;
import org.example.amortizationhelper.Tools.KopAndelTools;
import org.example.amortizationhelper.Tools.StartlistaTools;
import org.example.amortizationhelper.Tools.TravTools;
import org.example.amortizationhelper.WebSearch.WebSearchTools;
import org.example.amortizationhelper.mcp.TrackAwareGeocodingToolCallback;
import org.example.amortizationhelper.mcp.TrackWeatherTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AiChatConfig {

    private static final Logger log = LoggerFactory.getLogger(AiChatConfig.class);

    @Bean
    public ChatMemoryRepository chatMemoryRepository(
            @Value("${app.chat.memory.session-ttl:PT6H}") Duration sessionTtl) {
        return new ExpiringInMemoryChatMemory(sessionTtl);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 VectorStore vectorStore,
                                 ResourceLoader resourceLoader,
                                 TravTools travTools,
                                 StartlistaTools startlistaTools,
                                 EmailTools emailTools,
                                 KopAndelTools kopAndelTools,
                                 TrackWeatherTools trackWeatherTools,
                                 //RoiTools roiTools,
                                 ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider,
                                 ObjectMapper objectMapper,
                                 ChatMemoryRepository chatMemoryRepository,
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
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(12)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();

        ToolCallback[] mcpCallbacks = loadMcpCallbacks(mcpSyncClientsProvider, objectMapper);

        return builder
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, trackWeatherTools, webSearchTools, emailTools, kopAndelTools) //roiTools temp removed 2026-03-14
                .defaultToolCallbacks(mcpCallbacks)
                .build();
    }

    private ToolCallback[] loadMcpCallbacks(ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider,
                                            ObjectMapper objectMapper) {
        try {
            List<McpSyncClient> clients = mcpSyncClientsProvider.stream()
                    .flatMap(List::stream)
                    .toList();

            if (clients.isEmpty()) {
                log.info("No MCP clients configured.");
                return new ToolCallback[0];
            }

            for (McpSyncClient client : clients) {
                if (!client.isInitialized()) {
                    client.initialize();
                }
            }

            ToolCallback[] callbacks = wrapWeatherGeocodingCallbacks(
                    new SyncMcpToolCallbackProvider(clients).getToolCallbacks(),
                    objectMapper
            );
            log.info("Loaded {} MCP tool callback(s).", callbacks.length);
            return callbacks;
        } catch (Exception e) {
            log.warn("MCP tools are unavailable ({}). Chat and voice will start without MCP callbacks.",
                    rootCauseMessage(e));
            log.debug("MCP initialization failure", e);
            return new ToolCallback[0];
        }
    }

    private ToolCallback[] wrapWeatherGeocodingCallbacks(ToolCallback[] callbacks, ObjectMapper objectMapper) {
        return Arrays.stream(callbacks)
                .map(callback -> isWeatherGeocoding(callback)
                        ? new TrackAwareGeocodingToolCallback(callback, objectMapper)
                        : callback)
                .toArray(ToolCallback[]::new);
    }

    private boolean isWeatherGeocoding(ToolCallback callback) {
        String toolName = callback.getToolDefinition().name();
        return toolName != null && toolName.toLowerCase().contains("weather_geocoding");
    }

    private String rootCauseMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
