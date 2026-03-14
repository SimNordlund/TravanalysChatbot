package org.example.amortizationhelper.WebSearch;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebSearchTools {

    private final OpenAiWebSearchService webSearchService;

    public WebSearchTools(OpenAiWebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @Tool(description = "Search the live web for fresh or changing information such as news, schedules, dates, prices, rules, weather, sports, releases, and current events. Avoid using this for stable facts or when internal knowledge, vector store, memory, or other local tools are enough.")
    public String searchWeb(
                             @ToolParam(description = "The exact web search question to look up") String query) {
        return webSearchService.search(query);
    }
}