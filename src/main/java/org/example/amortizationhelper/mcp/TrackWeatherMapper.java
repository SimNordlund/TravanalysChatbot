package org.example.amortizationhelper.mcp;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrackWeatherMapper {

    private static final double DIRECT_CONFIDENCE = 1.0;
    private static final double PHRASE_CONFIDENCE = 0.95;
    private static final double FUZZY_CONFIDENCE = 0.82;

    private TrackWeatherMapper() {
    }

    private static final Map<String, WeatherLocation> TRACK_TO_WEATHER_LOCATION = Map.ofEntries(
            entry("arvika", "Arvika", "Arvika", "Sweden"),
            entry("axevalla", "Axevalla", "Skara", "Sweden"),
            entry("bergsaker", "Bergsåker", "Sundsvall", "Sweden"),
            entry("boden", "Boden", "Boden", "Sweden"),
            entry("bollnas", "Bollnäs", "Bollnäs", "Sweden"),
            entry("dannero", "Dannero", "Kramfors", "Sweden"),
            entry("dala jarna", "Dala Järna", "Dala-Järna", "Sweden"),
            entry("eskilstuna", "Eskilstuna", "Eskilstuna", "Sweden"),
            entry("jagersro", "Jägersro", "Malmö", "Sweden"),
            entry("farjestad", "Färjestad", "Karlstad", "Sweden"),
            entry("gavle", "Gävle", "Gävle", "Sweden"),
            entry("goteborg trav", "Göteborg Trav", "Gothenburg", "Sweden"),
            entry("hagmyren", "Hagmyren", "Hudiksvall", "Sweden"),
            entry("halmstad", "Halmstad", "Halmstad", "Sweden"),
            entry("hoting", "Hoting", "Hoting", "Sweden"),
            entry("karlshamn", "Karlshamn", "Karlshamn", "Sweden"),
            entry("kalmar", "Kalmar", "Kalmar", "Sweden"),
            entry("lindesberg", "Lindesberg", "Lindesberg", "Sweden"),
            entry("lycksele", "Lycksele", "Lycksele", "Sweden"),
            entry("mantorp", "Mantorp", "Mantorp", "Sweden"),
            entry("oviken", "Oviken", "Oviken", "Sweden"),
            entry("romme", "Romme", "Borlänge", "Sweden"),
            entry("rattvik", "Rättvik", "Rättvik", "Sweden"),
            entry("solvalla", "Solvalla", "Stockholm", "Sweden"),
            entry("skelleftea", "Skellefteå", "Skellefteå", "Sweden"),
            entry("solanget", "Solänget", "Örnsköldsvik", "Sweden"),
            entry("tingsryd", "Tingsryd", "Tingsryd", "Sweden"),
            entry("taby trav", "Täby Trav", "Täby", "Sweden"),
            entry("umaker", "Umåker", "Umeå", "Sweden"),
            entry("vemdalen", "Vemdalen", "Vemdalen", "Sweden"),
            entry("vaggeryd", "Vaggeryd", "Vaggeryd", "Sweden"),
            entry("visby", "Visby", "Visby", "Sweden"),
            entry("aby", "Åby", "Mölndal", "Sweden"),
            entry("amal", "Åmål", "Åmål", "Sweden"),
            entry("arjang", "Årjäng", "Årjäng", "Sweden"),
            entry("orebro", "Örebro", "Örebro", "Sweden"),
            entry("ostersund", "Östersund", "Östersund", "Sweden"),
            entry("bro park trav", "Bro Park Trav", "Bro, Upplands-Bro", "Sweden"),
            entry("broparktrav", "Bro Park Trav", "Bro, Upplands-Bro", "Sweden"),

            entry("bjerke", "Bjerke", "Oslo", "Norway"),
            entry("jarlsberg", "Jarlsberg", "Tønsberg", "Norway"),
            entry("momarken", "Momarken", "Mysen", "Norway"),
            entry("forus", "Forus", "Stavanger", "Norway"),
            entry("leangen", "Leangen", "Trondheim", "Norway"),
            entry("klosterskogen", "Klosterskogen", "Skien", "Norway"),
            entry("drammen", "Drammen", "Drammen", "Norway"),
            entry("bergen", "Bergen", "Bergen", "Norway"),
            entry("harstad", "Harstad", "Harstad", "Norway"),
            entry("haugaland", "Haugaland", "Haugesund", "Norway"),
            entry("bodo", "Bodø", "Bodø", "Norway"),
            entry("biri", "Biri", "Biri", "Norway"),
            entry("sorlandet", "Sørlandet", "Kristiansand", "Norway"),
            entry("orkla", "Orkla", "Orkanger", "Norway"),

            entry("charlottenlund", "Charlottenlund", "Copenhagen", "Denmark"),
            entry("odense", "Odense", "Odense", "Denmark"),
            entry("skive", "Skive", "Skive", "Denmark"),
            entry("alborg", "Aalborg", "Aalborg", "Denmark"),
            entry("arhus", "Aarhus", "Aarhus", "Denmark"),
            entry("bornholm", "Bornholm", "Bornholm", "Denmark"),
            entry("nykobing", "Nykøbing Falster", "Nykøbing Falster", "Denmark"),
            entry("billund", "Billund", "Billund", "Denmark"),

            entry("vermo", "Vermo", "Espoo", "Finland"),
            entry("abo", "Åbo", "Turku", "Finland"),
            entry("forssa", "Forssa", "Forssa", "Finland"),
            entry("harma", "Härmä", "Ylihärmä", "Finland"),
            entry("joensuu", "Joensuu", "Joensuu", "Finland"),
            entry("jyvaskyla", "Jyväskylä", "Jyväskylä", "Finland"),
            entry("kaustinen", "Kaustinen", "Kaustinen", "Finland"),
            entry("kouvola", "Kouvola", "Kouvola", "Finland"),
            entry("kuopio", "Kuopio", "Kuopio", "Finland"),
            entry("lahti", "Lahti", "Lahti", "Finland"),
            entry("lappeenranta", "Lappeenranta", "Lappeenranta", "Finland"),
            entry("loviisa", "Loviisa", "Loviisa", "Finland"),
            entry("mariehamn", "Mariehamn", "Mariehamn", "Finland"),
            entry("mikkeli", "Mikkeli", "Mikkeli", "Finland"),
            entry("pori", "Pori", "Pori", "Finland"),
            entry("rovaniemi", "Rovaniemi", "Rovaniemi", "Finland"),
            entry("seinajoki", "Seinäjoki", "Seinäjoki", "Finland"),
            entry("tammerfors", "Tammerfors", "Tampere", "Finland"),
            entry("tornea", "Torneå", "Tornio", "Finland"),
            entry("uleaborg", "Uleåborg", "Oulu", "Finland"),
            entry("ylivieska", "Ylivieska", "Ylivieska", "Finland")
    );

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("stockholm", "solvalla"),
            Map.entry("solvalla trav", "solvalla"),
            Map.entry("solvalla travbana", "solvalla"),
            Map.entry("malmo", "jagersro"),
            Map.entry("malmö", "jagersro"),
            Map.entry("jagersro trav", "jagersro"),
            Map.entry("jagersro travbana", "jagersro"),
            Map.entry("jaegersro", "jagersro"),
            Map.entry("karlstad", "farjestad"),
            Map.entry("farjestads trav", "farjestad"),
            Map.entry("farjestads travbana", "farjestad"),
            Map.entry("farjestadstravet", "farjestad"),
            Map.entry("farejstad", "farjestad"),
            Map.entry("farestad", "farjestad"),
            Map.entry("färjestad", "farjestad"),
            Map.entry("färejstad", "farjestad"),
            Map.entry("molndal", "aby"),
            Map.entry("mölndal", "aby"),
            Map.entry("aby trav", "aby"),
            Map.entry("abytravet", "aby"),
            Map.entry("borlange", "romme"),
            Map.entry("borlänge", "romme"),
            Map.entry("sundsvall", "bergsaker"),
            Map.entry("umea", "umaker"),
            Map.entry("umeå", "umaker"),
            Map.entry("ornskoldsvik", "solanget"),
            Map.entry("örnsköldsvik", "solanget"),
            Map.entry("skara", "axevalla"),
            Map.entry("bro park", "bro park trav"),
            Map.entry("bropark", "bro park trav"),
            Map.entry("taby", "taby trav"),
            Map.entry("täby", "taby trav")
    );

    public static WeatherLocation resolve(String trackName) {
        if (trackName == null || trackName.isBlank()) {
            return WeatherLocation.unknown("", "", 0);
        }

        String normalized = normalize(trackName);

        WeatherLocation direct = locationForKey(resolveAlias(normalized));
        if (direct != null) {
            return direct.withRequested(trackName, DIRECT_CONFIDENCE);
        }

        WeatherLocation phrase = resolveFromPhrase(trackName, normalized);
        if (phrase != null) {
            return phrase;
        }

        WeatherLocation fuzzy = resolveFuzzy(trackName, normalized);
        if (fuzzy != null) {
            return fuzzy;
        }

        return WeatherLocation.unknown(trackName, trackName, 0);
    }

    public static String toWeatherQuery(String trackName) {
        return resolve(trackName).weatherQuery();
    }

    private static WeatherLocation resolveFromPhrase(String requested, String normalized) {
        List<String> candidates = TRACK_TO_WEATHER_LOCATION.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        for (String key : candidates) {
            if (containsNormalizedTerm(normalized, key)) {
                return locationForKey(key).withRequested(requested, PHRASE_CONFIDENCE);
            }
        }

        List<Map.Entry<String, String>> aliases = ALIASES.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getKey().length()))
                .toList();

        for (Map.Entry<String, String> alias : aliases) {
            if (containsNormalizedTerm(normalized, normalize(alias.getKey()))) {
                return locationForKey(alias.getValue()).withRequested(requested, PHRASE_CONFIDENCE);
            }
        }

        return null;
    }

    private static WeatherLocation resolveFuzzy(String requested, String normalized) {
        String compact = normalized.replace(" ", "");
        if (compact.length() < 5) {
            return null;
        }

        String bestKey = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String key : TRACK_TO_WEATHER_LOCATION.keySet()) {
            String compactKey = key.replace(" ", "");
            int distance = levenshtein(compact, compactKey);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestKey = key;
            }
        }

        if (bestKey != null && bestDistance <= 2) {
            return locationForKey(bestKey).withRequested(requested, FUZZY_CONFIDENCE);
        }

        return null;
    }

    private static WeatherLocation locationForKey(String key) {
        return key == null ? null : TRACK_TO_WEATHER_LOCATION.get(key);
    }

    private static String resolveAlias(String normalized) {
        String aliasKey = ALIASES.get(normalized);
        return aliasKey == null ? normalized : aliasKey;
    }

    private static boolean containsNormalizedTerm(String normalized, String term) {
        String paddedNormalized = " " + normalized + " ";
        String paddedTerm = " " + normalize(term) + " ";
        return paddedNormalized.contains(paddedTerm);
    }

    private static Map.Entry<String, WeatherLocation> entry(String key, String track, String city, String country) {
        return Map.entry(key, WeatherLocation.known(track, city, country));
    }

    private static String normalize(String value) {
        String s = value.toLowerCase(Locale.ROOT).trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^a-z0-9 ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }

    public record WeatherLocation(
            String requested,
            String matchedTrack,
            String city,
            String country,
            String weatherQuery,
            boolean knownTrack,
            double confidence
    ) {

        private static WeatherLocation known(String track, String city, String country) {
            return new WeatherLocation("", track, city, country, city + ", " + country, true, DIRECT_CONFIDENCE);
        }

        private static WeatherLocation unknown(String requested, String weatherQuery, double confidence) {
            return new WeatherLocation(requested, "", "", "", weatherQuery, false, confidence);
        }

        private WeatherLocation withRequested(String requested, double confidence) {
            return new WeatherLocation(requested, matchedTrack, city, country, weatherQuery, knownTrack, confidence);
        }
    }
}
