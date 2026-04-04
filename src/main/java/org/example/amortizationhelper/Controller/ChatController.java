package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Email.EmailTools;
import org.example.amortizationhelper.Tools.KopAndelTools;
import org.example.amortizationhelper.Tools.StartlistaTools;
import org.example.amortizationhelper.Tools.TravTools;
import org.example.amortizationhelper.WebSearch.WebSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;

    @Autowired
    public ChatController(ChatClient.Builder builder,
                          VectorStore vectorStore,
                          ResourceLoader resourceLoader,
                          TravTools travTools,
                          StartlistaTools startlistaTools,
                          KopAndelTools kopAndelTools,
                          //RoiTools roiTools,
                          EmailTools emailTools,
                          SyncMcpToolCallbackProvider mcpToolCallbackProvider, //MCP
                          WebSearchTools webSearchTools) throws Exception {

        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .build();

        Resource promptRes = resourceLoader.getResource("classpath:/prompts/travPrompt.st");
        String templateString;
        try (var in = promptRes.getInputStream()) {
            templateString = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }

        PromptTemplate template = PromptTemplate.builder()
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
                .maxMessages(15)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();

        ToolCallback[] mcpCallbacks = mcpToolCallbackProvider.getToolCallbacks();

        this.chatClient = builder
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, webSearchTools, emailTools, kopAndelTools) //roiTools temp removed
                .defaultToolCallbacks(mcpCallbacks)
                .build();
    }

    @GetMapping("/chat-stream")
    public Flux<String> chatStream(@RequestParam("message") String message) {
        String clean = message.replaceAll("\\p{C}", "");
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User message: {}", requestId, clean);
        StringBuilder responseBuf = new StringBuilder();

        return chatClient.prompt()
                .user(clean)
                .stream()
                .content()
                .doOnNext(responseBuf::append)
                .doOnComplete(() -> log.info("[{}] Assistant response: {}", requestId, responseBuf))
                .doOnError(e -> log.error("[{}] Chat stream error", requestId, e));
    }
}
