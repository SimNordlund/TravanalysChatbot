package org.example.amortizationhelper.chat;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared knowledge and behaviour, with fresh date context on every request. */
@Component
public class TravoltaPromptService {

    private static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");
    private static final DateTimeFormatter SWEDISH_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.forLanguageTag("sv-SE"));

    private final String basePrompt;
    private final String voicePrompt;

    public TravoltaPromptService(ResourceLoader resources) throws IOException {
        basePrompt = read(resources, "classpath:/prompts/travPrompt.st")
                + "\n\nWEBBPLATSGUIDE\n"
                + read(resources, "classpath:/knowledge/travanalys-guide.txt");
        voicePrompt = read(resources, "classpath:/prompts/voicePrompt.txt");
    }

    public String forText() {
        return basePrompt + dateContext() + "\nSvarsläge: skrift. Använd läsbar vanlig text och korta stycken.\n";
    }

    public String forVoice() {
        return basePrompt + dateContext() + "\n\n" + voicePrompt;
    }

    private String dateContext() {
        ZonedDateTime now = ZonedDateTime.now(STOCKHOLM);
        LocalDate today = now.toLocalDate();
        return "\n\nAKTUELL DATUMKONTEXT (Europe/Stockholm)\n"
                + "Idag är " + today.format(SWEDISH_DATE) + " (" + today + ").\n"
                + "Aktuell tid: " + now.format(DateTimeFormatter.ofPattern("HH:mm XXX")) + ".\n"
                + "Igår: " + today.minusDays(1) + ". Imorgon: " + today.plusDays(1) + ".\n"
                + "Använd detta datum även om äldre meddelanden har annan datumkontext.\n";
    }

    private static String read(ResourceLoader resources, String location) throws IOException {
        try (var input = resources.getResource(location).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
    }
}
