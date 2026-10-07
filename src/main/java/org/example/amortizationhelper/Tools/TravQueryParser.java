package org.example.amortizationhelper.Tools;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared interpretation of user input. Ambiguous race references are never guessed. */
final class TravQueryParser {
    private TravQueryParser() { }

    private static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");
    private static final List<String> MONTHS = List.of("januari", "februari", "mars", "april", "maj", "juni",
            "juli", "augusti", "september", "oktober", "november", "december");
    private static final String MONTH_PATTERN = String.join("|", MONTHS);
    private static final Map<String, String> TRACKS = Map.ofEntries(
            Map.entry("Ar", "Arvika"), Map.entry("Ax", "Axevalla"), Map.entry("B", "Bergsåker"),
            Map.entry("Bo", "Boden"), Map.entry("Bs", "Bollnäs"), Map.entry("D", "Dannero"),
            Map.entry("Dj", "Dala Järna"), Map.entry("E", "Eskilstuna"), Map.entry("J", "Jägersro"),
            Map.entry("F", "Färjestad"), Map.entry("G", "Gävle"), Map.entry("Gt", "Göteborg trav"),
            Map.entry("H", "Hagmyren"), Map.entry("Hd", "Halmstad"), Map.entry("Hg", "Hoting"),
            Map.entry("Kh", "Karlshamn"), Map.entry("Kr", "Kalmar"), Map.entry("L", "Lindesberg"),
            Map.entry("Ly", "Lycksele"), Map.entry("Mp", "Mantorp"), Map.entry("Ov", "Oviken"),
            Map.entry("Ro", "Romme"), Map.entry("Rä", "Rättvik"), Map.entry("S", "Solvalla"),
            Map.entry("Sk", "Skellefteå"), Map.entry("Sä", "Solänget"), Map.entry("Ti", "Tingsryd"),
            Map.entry("Tt", "Täby Trav"), Map.entry("U", "Umåker"), Map.entry("Vd", "Vemdalen"),
            Map.entry("Vg", "Vaggeryd"), Map.entry("Vi", "Visby"), Map.entry("Å", "Åby"),
            Map.entry("Åm", "Åmål"), Map.entry("År", "Årjäng"), Map.entry("Ö", "Örebro"),
            Map.entry("Ös", "Östersund"));
    private static final Pattern FORM = Pattern.compile(
            "(?<![a-z0-9])(vinnare|plats|v85|v86|v75|gs75|v64|v65|v5|v4|v3|dd|ld|trio|tvilling|komb|trippel|triple)(?![a-z0-9])");

    static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    static Integer date(String value) {
        if (value == null || value.isBlank()) return null;
        String n = normalize(value);
        LocalDate today = LocalDate.now(STOCKHOLM);
        Matcher compact = Pattern.compile("(?<!\\d)(\\d{4})(\\d{2})(\\d{2})(?!\\d)").matcher(n);
        if (compact.find()) return validDate(compact.group(1), compact.group(2), compact.group(3));
        Matcher iso = Pattern.compile("(?<!\\d)(\\d{4})[-/ ](\\d{1,2})[-/ ](\\d{1,2})(?!\\d)").matcher(n);
        if (iso.find()) return validDate(iso.group(1), iso.group(2), iso.group(3));
        Matcher ydm = Pattern.compile("\\b(\\d{4})\\s+(\\d{1,2})\\s+(" + MONTH_PATTERN + ")\\b").matcher(n);
        if (ydm.find()) return validDate(ydm.group(1), String.valueOf(MONTHS.indexOf(ydm.group(3)) + 1), ydm.group(2));
        Matcher words = Pattern.compile("\\b(\\d{1,2})(?::[ae])?\\s+(" + MONTH_PATTERN + ")(?:\\s+(\\d{4}))?\\b").matcher(n);
        if (words.find()) return validDate(words.group(3) == null ? String.valueOf(today.getYear()) : words.group(3),
                String.valueOf(MONTHS.indexOf(words.group(2)) + 1), words.group(1));
        Matcher swedish = Pattern.compile("(?<![\\d/-])(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}))?(?![\\d/-])").matcher(n);
        if (swedish.find()) return validDate(swedish.group(3) == null ? String.valueOf(today.getYear()) : swedish.group(3),
                swedish.group(2), swedish.group(1));
        if (Pattern.compile("\\b(i overmorgon|iovermorgon)\\b").matcher(n).find()) return asInt(today.plusDays(2));
        if (Pattern.compile("\\b(i forrgar|iforrgar)\\b").matcher(n).find()) return asInt(today.minusDays(2));
        if (Pattern.compile("\\b(i morgon|imorgon)\\b").matcher(n).find()) return asInt(today.plusDays(1));
        if (Pattern.compile("\\b(i gar|igar)\\b").matcher(n).find()) return asInt(today.minusDays(1));
        if (Pattern.compile("\\b(i dag|idag)\\b").matcher(n).find()) return asInt(today);
        return null;
    }

    private static Integer validDate(String year, String month, String day) {
        try {
            return asInt(LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static int asInt(LocalDate date) {
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    static String track(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        // Keep Swedish letters in codes: Ar (Arvika) and År (Årjäng) are different tracks.
        for (String code : TRACKS.keySet()) if (code.equalsIgnoreCase(trimmed)) return code;
        String n = normalize(trimmed);
        Set<String> matches = new HashSet<>();
        TRACKS.forEach((code, name) -> {
            if (Pattern.compile("(?<![a-z])" + Pattern.quote(normalize(name)) + "(?![a-z])").matcher(n).find()) matches.add(code);
        });
        if (!matches.isEmpty()) return matches.size() == 1 ? matches.iterator().next() : null;
        // In prose require an introducer. Ordinary words such as Vi and År are also valid codes.
        TRACKS.keySet().forEach(code -> {
            String token = Pattern.quote(code);
            if (Pattern.compile("(?iu)\\b(?:bankod|bana|på|pa)\\s+" + token + "(?![\\p{L}\\d])").matcher(trimmed).find()) matches.add(code);
        });
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    static String race(String value) {
        if (value == null || value.isBlank()) return null;
        String n = normalize(value);
        if (n.matches("[0-9]{1,2}")) return positiveRace(n);
        Matcher explicit = Pattern.compile("\\blopp(?:et)?\\s*(?:nr\\.?\\s*)?[:#-]?\\s*(\\d{1,2})\\b").matcher(n);
        if (explicit.find()) return positiveRace(explicit.group(1));
        // Avdelning and e.g. V85-1 need an independently verified mapping to a physical race.
        return null;
    }

    private static String positiveRace(String value) {
        int number = Integer.parseInt(value);
        return number > 0 ? String.valueOf(number) : null;
    }

    static String form(String value) {
        if (value == null || value.isBlank()) return null;
        String n = normalize(value).replaceAll("\\b(v|gs)\\s+(\\d{1,3})\\b", "$1$2");
        Matcher explicit = Pattern.compile("\\bspelform\\s*[:=]?\\s*([a-z0-9]+)").matcher(n);
        if (explicit.find()) return explicit.group(1);
        Matcher known = FORM.matcher(n);
        if (known.find()) return known.group(1);
        Matcher unknownPool = Pattern.compile("\\b(?:v|gs)\\d{1,3}\\b").matcher(n);
        if (unknownPool.find()) return unknownPool.group();
        if (n.matches("[a-z]+[0-9]*")) return n;
        return null;
    }

    static Integer starter(String value) {
        if (value == null || value.isBlank()) return null;
        String n = normalize(value);
        Matcher m = Pattern.compile("(?:^|\\b)(\\d{1,2})\\s*starter\\b|\\bstarter\\s*[:=]?\\s*(\\d{1,2})\\b|^(\\d{1,2})$").matcher(n);
        if (!m.find()) return null;
        for (int i = 1; i <= 3; i++) if (m.group(i) != null) return Integer.valueOf(m.group(i));
        return null;
    }

    static Double percent(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim().replace("\u00a0", " ").replace("%", "").trim().replace(',', '.');
        if (!cleaned.matches("\\d+(?:\\.\\d+)?")) return null;
        try {
            double parsed = Double.parseDouble(cleaned);
            return Double.isFinite(parsed) && parsed <= 100 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
