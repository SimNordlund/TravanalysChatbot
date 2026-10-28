package org.example.amortizationhelper.Tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Anropar samma reduceringsmotor som webbplatsen, utan att styra en webbläsare. */
@Service
public class ReductionService {

    private static final Map<String, Integer> LEG_COUNTS = Map.of(
            "Trio", 3, "V3", 3, "V4", 4, "V5", 5, "V64", 6,
            "V65", 6, "GS75", 7, "V85", 8, "V86", 8);
    private static final Duration PREVIEW_LIFETIME = Duration.ofMinutes(15);
    private static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");
    private static final int MAX_PREVIEWS = 1000;

    private final RestClient client;
    private final URI baseUri;
    private final long maxCombinations;
    // Ett sparat underlag per samtal. Ingen gemensam "senaste fil" kan blandas mellan samtal.
    private final Map<String, PreparedPreview> previews = new LinkedHashMap<>();

    public ReductionService(
            @Value("${app.reduction.base-url:https://travanalys-reducering-backend-latest.onrender.com}") String baseUrl,
            @Value("${app.reduction.connect-timeout:10s}") Duration connectTimeout,
            @Value("${app.reduction.read-timeout:120s}") Duration readTimeout,
            @Value("${app.reduction.max-combinations:1000000}") long maxCombinations) {
        baseUri = URI.create(baseUrl.replaceAll("/+$", ""));
        if (baseUri.getHost() == null || baseUri.getRawUserInfo() != null
                || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || !("https".equals(baseUri.getScheme()) || "http".equals(baseUri.getScheme()))
                || maxCombinations < 1 || maxCombinations > Integer.MAX_VALUE
                || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Invalid reduction API configuration");
        }
        this.maxCombinations = maxCombinations;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(baseUri.toString())
                .requestFactory(factory).defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE).build();
    }

    public Map<String, Object> options() {
        JsonNode options = getOptions();
        return Map.of("spelformer", options.path("spelformer"),
                "banor", options.path("trackCodes"),
                "standardvillkor", new ReductionRequest.Filter(0, 13, false, List.of()),
                "maxKombinationer", maxCombinations,
                "begransning", "En bana med följande faktiska loppnummer. Radpris måste anges eller verifieras.");
    }

    public Map<String, Object> preview(String conversationId, ReductionRequest request) {
        synchronized (previews) {
            // Ett misslyckat ändringsförsök får inte lämna det gamla systemet aktivt.
            previews.remove(conversationId);
        }
        Map<String, Object> payload = prepareRequest(request);
        JsonNode response = post("/api/reducering/preview", payload);
        int generated = nonNegativeInt(response, "generatedRows");
        int filtered = nonNegativeInt(response, "filteredRows");
        int missing = nonNegativeInt(response, "missingStreckHits");
        if (filtered > generated || generated > maxCombinations) {
            throw new IllegalStateException("Inconsistent reduction row counts");
        }
        var prepared = new PreparedPreview(UUID.randomUUID().toString(), payload, filtered,
                Instant.now().plus(PREVIEW_LIFETIME));
        synchronized (previews) {
            previews.values().removeIf(value -> !value.expiresAt.isAfter(Instant.now()));
            if (!previews.containsKey(conversationId) && previews.size() >= MAX_PREVIEWS) {
                previews.remove(previews.keySet().iterator().next());
            }
            previews.put(conversationId, prepared);
        }
        Map<String, Object> result = summary(prepared, filtered);
        result.put("genereradeRader", generated);
        result.put("saknadeStreckTraffar", missing);
        result.put("kanSkapaFil", filtered > 0);
        result.put("information", filtered == 0
                ? "Inga rader klarar villkoren. Ändra val eller villkor och förhandsgranska igen."
                : "Förhandsgranskning klar. Skapa filen om användaren bett om den; annars visa sammanfattningen först.");
        return result;
    }

    public Map<String, Object> createFile(String conversationId, String previewId) {
        PreparedPreview prepared;
        synchronized (previews) {
            prepared = previews.get(conversationId);
            if (prepared == null || !prepared.id.equals(previewId)
                    || !prepared.expiresAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("Förhandsgranskningen saknas eller har gått ut. Förhandsgranska systemet igen.");
            }
        }
        // Återanvänd exakt de granskade valen; modellen får inte återskapa filens indata.
        // Lås bara detta underlag så att andra samtal kan skapa filer samtidigt.
        synchronized (prepared) {
            if (prepared.filteredRows == 0) {
                throw new IllegalArgumentException("Systemet har inga kvarvarande rader. Ändra villkoren först.");
            }
            if (prepared.file != null && prepared.fileExpiresAt.isAfter(Instant.now())) {
                return prepared.file;
            }
            JsonNode response = post("/api/reducering/xml/temp", prepared.payload);
            int coupons = nonNegativeInt(response, "couponCount");
            if (coupons != prepared.filteredRows) {
                throw new IllegalArgumentException("Radantalet har ändrats. Förhandsgranska systemet på nytt innan filen används.");
            }
            URI url = validateFileUrl(response.path("url").asText());
            Instant expiresAt = Instant.parse(response.path("expiresAt").asText());
            String filename = response.path("fileName").asText();
            if (!expiresAt.isAfter(Instant.now()) || filename.isBlank() || !filename.endsWith(".xml")) {
                throw new IllegalStateException("Invalid temporary XML response");
            }
            Map<String, Object> result = summary(prepared, coupons);
            result.put("url", url.toString());
            result.put("filnamn", filename);
            result.put("giltigTill", expiresAt.toString());
            // Returnera även svensk lokal tid så att modellen inte behöver räkna om sommar-/vintertid.
            result.put("giltigTillSvenskTid", expiresAt.atZone(STOCKHOLM)
                    .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.forLanguageTag("sv-SE"))));
            result.put("saknadeStreckTraffar", nonNegativeInt(response, "missingStreckHits"));
            result.put("information", "XML-filen är skapad. Visa den faktiska länken och giltighetstiden i Europe/Stockholm. "
                    + "Länken är tillfällig och kan sluta fungera vid en omstart av reduceringstjänsten. "
                    + "Den kan skickas med send_email endast om användaren uttryckligen bett om mejl till sin angivna adress. "
                    + "Inget spel har lämnats in. Kostnaden är beräknad från det angivna radpriset, inte verifierad av ATG.");
            prepared.file = Map.copyOf(result);
            prepared.fileExpiresAt = expiresAt;
            return prepared.file;
        }
    }

    private Map<String, Object> prepareRequest(ReductionRequest request) {
        if (request == null) throw new IllegalArgumentException("Ange systemets val och villkor.");
        String form = request.spelform() == null ? "" : request.spelform().trim().toUpperCase(Locale.ROOT);
        if (form.equals("TRIO")) form = "Trio";
        Integer legCount = LEG_COUNTS.get(form);
        if (legCount == null) throw new IllegalArgumentException("Välj Trio, V3, V4, V5, V64, V65, GS75, V85 eller V86.");
        LocalDate date;
        try {
            String value = request.datum() == null ? "" : request.datum().trim();
            date = value.matches("\\d{8}") ? LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE) : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ange ett giltigt tävlingsdatum, exempelvis 2026-10-27.");
        }
        if (request.startlopp() == null || request.startlopp() < 1 || request.startlopp() > 99) {
            throw new IllegalArgumentException("Ange det faktiska startloppet. Ett avdelningsnummer räcker inte.");
        }
        if (request.radpris() == null || request.radpris().signum() <= 0 || request.radpris().scale() > 2) {
            throw new IllegalArgumentException("Ange eller verifiera radpriset i kronor med högst två decimaler.");
        }
        if (request.avdelningar() == null || request.avdelningar().size() != legCount) {
            throw new IllegalArgumentException("Spelformen kräver exakt " + legCount + " hästlistor i rankordning.");
        }
        int maxHorse = form.equals("V4") ? 20 : 15;
        List<List<Integer>> selections = new ArrayList<>();
        long combinations = 1;
        for (List<Integer> leg : request.avdelningar()) {
            if (leg == null || leg.isEmpty() || leg.stream().anyMatch(n -> n == null || n < 1 || n > maxHorse)) {
                throw new IllegalArgumentException("Fyll varje lista med hästnummer mellan 1 och " + maxHorse + ".");
            }
            // Ta bort dubbletter utan att sortera: rankvillkoren bygger på ordningen.
            List<Integer> unique = leg.stream().distinct().toList();
            if (combinations > maxCombinations / unique.size()) {
                throw new IllegalArgumentException("Systemet överstiger gränsen på " + maxCombinations
                        + " kombinationer före reducering. Välj färre hästar.");
            }
            combinations *= unique.size();
            selections.add(unique);
        }
        ReductionRequest.Filter input = request.filter();
        int minRank = input == null || input.minRank() == null ? 0 : input.minRank();
        int maxRank = input == null || input.maxRank() == null ? 13 : input.maxRank();
        if (minRank < 0 || maxRank < minRank) {
            throw new IllegalArgumentException("Min rank måste vara minst 0 och får inte överstiga Max rank.");
        }
        List<String> excluded = input == null || input.excludedTextRows() == null ? List.of() : input.excludedTextRows();
        if (excluded.size() > 1000 || excluded.stream().anyMatch(row -> row == null || !row.matches("\\d{16}"))) {
            throw new IllegalArgumentException("Exkluderade textrader måste ha exakt 16 siffror; högst 1000 rader får anges.");
        }
        var filter = new ReductionRequest.Filter(minRank, maxRank,
                input != null && Boolean.TRUE.equals(input.requireAtLeastTwoFirstRanked()), List.copyOf(excluded));
        JsonNode options = getOptions();
        boolean supported = false;
        for (JsonNode option : options.path("spelformer")) supported |= form.equals(option.asText());
        if (!supported) throw new IllegalArgumentException("Spelformen är inte tillgänglig i reduceringstjänsten just nu.");
        String bankod = TravQueryParser.track(request.bana());
        // Reduceringsmotorn använder Ho för Hoting; analysens gemensamma namnparser använder Hg.
        if ("Hg".equals(bankod)) bankod = "Ho";
        JsonNode selectedTrack = null;
        for (JsonNode track : options.path("trackCodes")) {
            if (track.path("name").asText().equalsIgnoreCase(bankod)
                    || track.path("name").asText().equalsIgnoreCase(request.bana() == null ? "" : request.bana().trim())
                    || track.path("code").asText().equals(request.bana() == null ? "" : request.bana().trim())) {
                selectedTrack = track;
                break;
            }
        }
        if (selectedTrack == null) throw new IllegalArgumentException("Ange en enda bana som stöds av reduceringen.");
        return Map.of("spelform", form, "startDatum", date.format(DateTimeFormatter.BASIC_ISO_DATE),
                "banKod", selectedTrack.path("name").asText(), "trackCode", selectedTrack.path("code").asText(),
                "lopp", request.startlopp(), "radpris", request.radpris(),
                "avdelningar", List.copyOf(selections), "filter", filter);
    }

    private JsonNode getOptions() {
        JsonNode response = client.get().uri("/api/reducering/options").retrieve().body(JsonNode.class);
        if (response == null || !response.path("spelformer").isArray() || !response.path("trackCodes").isArray()) {
            throw new IllegalStateException("Invalid reduction options response");
        }
        return response;
    }

    private JsonNode post(String path, Map<String, Object> payload) {
        // Ingen automatisk retry: ett avbrutet svar kan ändå ha skapat en fil på servern.
        JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(payload).retrieve().body(JsonNode.class);
        if (response == null || !response.isObject()) throw new IllegalStateException("Empty reduction response");
        return response;
    }

    private static int nonNegativeInt(JsonNode response, String field) {
        JsonNode value = response.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new IllegalStateException("Invalid reduction count: " + field);
        }
        return value.asInt();
    }

    private URI validateFileUrl(String value) {
        URI url;
        try {
            url = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid reduction file URL", e);
        }
        // Acceptera bara reduceringsserverns faktiska fillänkar, aldrig modellens egna adresser.
        if (!baseUri.getScheme().equals(url.getScheme()) || !baseUri.getHost().equalsIgnoreCase(url.getHost())
                || baseUri.getPort() != url.getPort() || url.getRawUserInfo() != null
                || url.getRawQuery() != null || url.getRawFragment() != null || url.getPath() == null
                || !url.getPath().matches("/api/reducering/xml/temp/[a-fA-F0-9]{32}(?:/[^/]+)?")) {
            throw new IllegalStateException("Unexpected reduction file URL");
        }
        return url;
    }

    private Map<String, Object> summary(PreparedPreview prepared, int rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("previewId", prepared.id);
        summary.put("valOchVillkor", prepared.payload);
        summary.put("kvarvarandeRader", rows);
        summary.put("radpris", prepared.payload.get("radpris"));
        summary.put("beraknadKostnadKr", ((BigDecimal) prepared.payload.get("radpris")).multiply(BigDecimal.valueOf(rows)));
        summary.put("kostnadsinformation", "Beräknat från angivet radpris. Kontrollera slutlig insats vid inlämning. "
                + "Förutom för Trio ändrar radpriset inte XML-filens insats.");
        return summary;
    }

    private static final class PreparedPreview {
        private final String id;
        private final Map<String, Object> payload;
        private final int filteredRows;
        private final Instant expiresAt;
        private Map<String, Object> file;
        private Instant fileExpiresAt;

        private PreparedPreview(String id, Map<String, Object> payload, int filteredRows, Instant expiresAt) {
            this.id = id;
            this.payload = payload;
            this.filteredRows = filteredRows;
            this.expiresAt = expiresAt;
        }
    }
}
