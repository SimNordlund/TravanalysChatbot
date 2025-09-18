// package justera efter ditt projekt
package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Tools.RoiTools;                          //Changed!
import org.example.amortizationhelper.Tools.StartlistaTools;                   //Changed!
import org.example.amortizationhelper.Tools.TravTools;                         //Changed!
import org.slf4j.Logger;                                                       //Changed!
import org.slf4j.LoggerFactory;                                                //Changed!
import org.springframework.ai.chat.client.ChatClient;                          //Changed!
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;    //Changed!
import org.springframework.ai.chat.memory.ChatMemory;                          //Changed!
import org.springframework.ai.chat.memory.MessageWindowChatMemory;             //Changed!
import org.springframework.ai.chat.prompt.PromptTemplate;                      //Changed!
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;        //Changed!
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter; //Changed!
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;    //Changed!
import org.springframework.ai.template.st.StTemplateRenderer;                  //Changed!
import org.springframework.ai.vectorstore.VectorStore;                         //Changed!
import org.springframework.beans.factory.annotation.Autowired;                 //Changed!
import org.springframework.core.io.Resource;                                   //Changed!
import org.springframework.core.io.ResourceLoader;                             //Changed!
import org.springframework.util.StreamUtils;                                   //Changed!
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;                                      //Changed!
import java.util.UUID;                                                         //Changed!

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class); //Changed!

    private final ChatClient chatClient;                                       //Changed!

    @Autowired                                                                  //Changed!
    public ChatController(ChatClient.Builder builder,                           //Changed!
                          VectorStore vectorStore,                               //Changed!
                          ResourceLoader resourceLoader,                         //Changed!
                          TravTools travTools,                                   //Changed!
                          StartlistaTools startlistaTools,                       //Changed!
                          RoiTools roiTools) throws Exception {                 //Changed!

        var retriever = VectorStoreDocumentRetriever.builder()                  //Changed!
                .vectorStore(vectorStore)
                .build();

        Resource promptRes = resourceLoader.getResource("classpath:/prompts/travPrompt.st"); //Changed!
        String templateString;
        try (var in = promptRes.getInputStream()) {
            templateString = StreamUtils.copyToString(in, StandardCharsets.UTF_8); //Changed!
        }

        PromptTemplate template = PromptTemplate.builder()                       //Changed!
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template(templateString)
                .build();

        var queryAugmenter = ContextualQueryAugmenter.builder()                  //Changed!
                .allowEmptyContext(true)
                .promptTemplate(template)
                .build();

        var ragAdvisor = RetrievalAugmentationAdvisor.builder()                  //Changed!
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter)
                .build();

        ChatMemory memory = MessageWindowChatMemory.builder()                    //Changed!
                .maxMessages(12)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();    //Changed!

        this.chatClient = builder                                               //Changed!
                .defaultAdvisors(ragAdvisor, memoryAdvisor)
                .defaultTools(travTools, startlistaTools, roiTools)
                .build();
    }

    @GetMapping("/chat-stream")
    public Flux<String> chatStream(@RequestParam("message") String message) {
        String clean = message.replaceAll("\\p{C}", "");
        String requestId = UUID.randomUUID().toString();                         //Changed!
        log.info("[{}] User message: {}", requestId, clean);                     //Changed!
        StringBuilder responseBuf = new StringBuilder();                          //Changed!

        return chatClient.prompt()
                .user(clean)
                .stream()
                .content()
                .doOnNext(responseBuf::append)                                   //Changed!
                .doOnComplete(() -> log.info("[{}] Assistant response: {}", requestId, responseBuf)) //Changed!
                .doOnError(e -> log.error("[{}] Chat stream error", requestId, e)); //Changed!
    }
}
