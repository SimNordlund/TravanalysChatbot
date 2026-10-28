package org.example.amortizationhelper.Tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class ReductionTools {
    public static final String CONVERSATION_CONTEXT_KEY = "reductionConversationId";
    private static final Logger log = LoggerFactory.getLogger(ReductionTools.class);
    private final ReductionService service;

    public ReductionTools(ReductionService service) {
        this.service = service;
    }

    @Tool(name = "reduction_options", description = "Hämta tillgängliga spelformer, banor och standardvillkor för att skapa egna reducerade XML-filer. Ger inte aktuellt radpris eller loppkopplingar.")
    public Object options() {
        return safely(service::options);
    }

    @Tool(name = "preview_reduced_system", description = "Förhandsgranska användarens system och spara exakta val i detta samtal. Returnerar previewId, rader och beräknad kostnad. Skapar ingen fil. Alla avdelningar måste vara följande faktiska lopp på EN bana. Fråga om nödvändiga uppgifter saknas; gissa inte hästval, startlopp eller radpris. Ändrade val kräver ny förhandsgranskning.")
    public Object preview(@ToolParam(description = "Systemets fullständiga val och rankvillkor") ReductionRequest request,
                          ToolContext context) {
        return safely(() -> service.preview(conversationId(context), request));
    }

    @Tool(name = "create_reduced_system_file", description = "Skapa en tillfällig XML-länk från en lyckad förhandsgranskning i samma samtal, endast när användaren bett om en fil eller länk. Återanvänder de sparade valen. Visa exakt returnerad URL och giltighetstid. Verktyget lämnar aldrig in spel och skickar inte mejl.")
    public Object createFile(@ToolParam(description = "Exakt previewId från senaste lyckade preview_reduced_system i detta samtal") String previewId,
                             ToolContext context) {
        return safely(() -> service.createFile(conversationId(context), previewId));
    }

    private String conversationId(ToolContext context) {
        // Samtalsnyckeln kommer från controllern och ingår inte i modellens verktygsparametrar.
        Object value = context == null ? null : context.getContext().get(CONVERSATION_CONTEXT_KEY);
        if (!(value instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("Samtalet kunde inte identifieras. Be användaren försöka igen i chatten.");
        }
        return id;
    }

    private Object safely(Supplier<Object> action) {
        try {
            return action.get();
        } catch (ResourceAccessException e) {
            log.warn("Reduction API could not be reached ({})", e.getClass().getSimpleName());
            return Map.of("error", "Reduceringsservern svarade inte i tid. Ingen färdig fil kan bekräftas. Försök igen senare.");
        } catch (RestClientResponseException e) {
            // API-fel kan innehålla databasdetaljer; visa inte råa svar eller stacktraces.
            log.warn("Reduction API returned HTTP {}", e.getStatusCode().value());
            return Map.of("error", e.getStatusCode().is4xxClientError()
                    ? "Reduceringsservern avvisade uppgifterna. Kontrollera datum, bana, lopp, hästval och villkor. Ingen fil kan bekräftas."
                    : "Reduceringen är inte tillgänglig just nu. Ingen fil kan bekräftas. Försök igen senare.");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage() == null ? "Kontrollera systemets uppgifter." : e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Reduction operation failed ({})", e.getClass().getSimpleName());
            return Map.of("error", "Reduceringssvaret kunde inte verifieras. Påstå inte att filen är klar och hitta inte på en länk.");
        }
    }
}
