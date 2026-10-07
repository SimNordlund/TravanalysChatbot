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

    @Tool(description = "Kontrollera aktuella travresultat, strykningar, starttider, odds, regler eller nyheter på webben. Använd när färska eller externa fakta behövs och intern data inte räcker. Returnerar svenska uppgifter med källänkar och kontrolltid, eller tydlig information om att uppgiften inte kunde verifieras. Använd inte för vanlig webbplatshjälp eller för att ersätta Travanalys egen ranking.")
    public String searchWeb(
                             @ToolParam(description = "Precis sökfråga med känt datum, bana, lopp/avdelning och hästnamn. Inga privata uppgifter eller hela chatthistoriken.") String query) {
        return webSearchService.search(query);
    }
}
