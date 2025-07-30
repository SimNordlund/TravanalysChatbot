package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Tools.RoiTools;
import org.example.amortizationhelper.Tools.StartlistaTools;
import org.example.amortizationhelper.Tools.TravTools;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;


@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder,
                          VectorStore vectorStore,
                          ResourceLoader resourceLoader,
                          TravTools travTools,
                          StartlistaTools startlistaTools,
                          RoiTools roiTools
    ) throws Exception {

        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                // .topK(40)
                // .similarityThreshold(0.05)
                .build();


        Resource promptRes = resourceLoader.getResource("classpath:/prompts/travPrompt.st");

        String templateString;
        try (var in = promptRes.getInputStream()) {
            templateString = StreamUtils.copyToString(
                    in, StandardCharsets.UTF_8);
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
                .maxMessages(12)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();

        this.chatClient = builder
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, roiTools)
                .build();
    }

    @GetMapping("/chat-stream")
    public Flux<String> chatStream(@RequestParam("message") String message) {
        String clean = message.replaceAll("\\p{C}", "");
        return chatClient.prompt()
                .user(clean)
                .stream()
                .content();
        //structured output
        //conversation ID för att alltid skriva HEJ SIMON
        //VIDEO DAN VEGA !
    }
}