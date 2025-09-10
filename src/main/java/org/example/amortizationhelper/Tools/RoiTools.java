package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Roi;
import org.example.amortizationhelper.repo.RoiRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO
//FELSÖK ROI RÄKNAR NOG FEL PGA FLERA STARTER BREE

@Component
@AllArgsConstructor
public class RoiTools {

    private final RoiRepo roiRepo;

    @Tool(name = "roi_dates_all", description = "Lista tillgängliga datum i ROI (senaste först).")
    public List<Integer> roiDatesAll() {
        return roiRepo.distinctDatesAll();
    }

    @Tool(name = "roi_tracks_by_date", description = "Lista bankoder som har ROI på ett datum.")
    public List<String> roiTracksByDate(String dateOrPhrase) {
        Integer d = toIntDateFlexible(dateOrPhrase);
        if (d == null) return List.of();
        return roiRepo.distinctBanKodByDate(d);
    }

    @Tool(name = "roi_laps_by_date_track", description = "Lista lopp/avd som har ROI för datum + bana.")
    public List<String> roiLapsByDateTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = toIntDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return roiRepo.distinctLapsByDateAndBanKod(d, b);
    }


    @Tool(name = "roi_by_date_track", description = "Hämta alla ROI-rader för datum + bana (alla lopp).")
    public List<RoiDto> roiByDateTrack(String date, String banKodOrTrack) {
        Integer d = toIntDateFlexible(date);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return roiRepo.fetchRoiByDateTrack(d, b).stream()
                .map(this::toDto).toList();
    }

    @Tool(name = "roi_by_date_track_lap",
            description = "Hämta ROI (totalt/vinnare/plats/trio) för datum, bana och lopp/avd. Accepterar svenska datum, bannamn/bankod samt 'lopp 5', 'avd 3' eller 'v75-1'. Returnerar även hästnamn och startnummer.")
    public List<RoiDto> roiByDateTrackLap(String date, String banKodOrTrack, String lapOrPhrase) {
        Integer d = toIntDateFlexible(date);
        if (d == null) return List.of();
        String normBan = resolveBanKodFlexible(banKodOrTrack);
        if (normBan == null) return List.of();
        String lap = parseLapOrAvdFlexible(lapOrPhrase);
        if (lap == null) return List.of();

        return roiRepo.fetchRoiByDateTrackLap(d, normBan, lap).stream()
                .map(this::toDto)
                .toList();
    }

    @Tool(name = "roi_top_by_date_track_lap",
            description = "Topp N i ett lopp baserat på metric: 'totalt' (default), 'vinnare', 'plats' eller 'trio'.")
    public List<RoiDto> roiTopByDateTrackLap(String date, String banKodOrTrack, String lapOrPhrase, String metric, Integer topN) {
        if (topN == null || topN <= 0) topN = 3;
        Integer d = toIntDateFlexible(date);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapOrAvdFlexible(lapOrPhrase);
        if (d == null || b == null || lap == null) return List.of();

        String m = metric == null ? "totalt" : metric.toLowerCase(Locale.ROOT).trim();
        List<Roi> rows = roiRepo.fetchRoiByDateTrackLap(d, b, lap);
        rows.sort((r1, r2) -> metricValue(r2, m).compareTo(metricValue(r1, m)));
        return rows.stream().limit(topN).map(this::toDto).toList();
    }

    // ===== Summor =====

    @Tool(name = "roi_total_by_date_track",
            description = "Summera ROI Totalt för alla hästar på ett datum och bana. Accepterar YYYYMMDD, YYYY-MM-DD eller svensk fras (t.ex. '17 juli 2025') samt bannamn/bankod.")
    public BigDecimal roiTotalByDateAndTrack(String date, String banKodOrTrack) {
        Integer d = toIntDateFlexible(date);
        if (d == null) return BigDecimal.ZERO;
        String normBan = resolveBanKodFlexible(banKodOrTrack);
        if (normBan == null) return BigDecimal.ZERO;
        // Hämta via repo-summa direkt
        BigDecimal sum = roiRepo.sumTotaltByDateTrack(d, normBan);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Tool(name = "roi_summary_by_date_track",
            description = "Summera ROI (Totalt, Vinnare, Plats, Trio) för datum + bana.")
    public RoiSummary roiSummaryByDateTrack(String date, String banKodOrTrack) {
        Integer d = toIntDateFlexible(date);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return new RoiSummary();
        Object[] arr = roiRepo.sumsByDateTrackAndOptionalLap(d, b, null);
        return mapSummary(null, arr);
    }

    @Tool(name = "roi_summary_by_date_track_lap",
            description = "Summera ROI (Totalt, Vinnare, Plats, Trio) för datum + bana + lopp/avd.")
    public RoiSummary roiSummaryByDateTrackLap(String date, String banKodOrTrack, String lapOrPhrase) {
        Integer d = toIntDateFlexible(date);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapOrAvdFlexible(lapOrPhrase);
        if (d == null || b == null || lap == null) return new RoiSummary();
        Object[] arr = roiRepo.sumsByDateTrackAndOptionalLap(d, b, lap);
        return mapSummary(lap, arr);
    }


    @Tool(name = "roi_by_horse", description = "Hitta ROI för en viss häst i datum + bana + lopp genom namnfragment.")
    public List<RoiDto> roiByHorse(String date, String banKodOrTrack, String lapOrPhrase, String nameFragment) {
        Integer d = toIntDateFlexible(date);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapOrAvdFlexible(lapOrPhrase);
        if (d == null || b == null || lap == null || nameFragment == null || nameFragment.isBlank()) return List.of();
        return roiRepo.fetchRoiByDTLAndHorseLike(d, b, lap, nameFragment).stream()
                .map(this::toDto).toList();
    }

    @Tool(name = "roi_by_number", description = "Hitta ROI för en viss häst via startnummer i datum + bana + lopp.")
    public RoiDto roiByNumber(String date, String banKodOrTrack, String lapOrPhrase, Integer number) {
        Integer d = toIntDateFlexible(date);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapOrAvdFlexible(lapOrPhrase);
        if (d == null || b == null || lap == null || number == null) return null;
        return roiRepo.findByDTLAndNumber(d, b, lap, number)
                .map(this::toDto).orElse(null);
    }


    @Tool(name = "roi_by_phrase",
            description = "Tolka en svensk fras (datum/bana/lopp) och hämta ROI. Ex: 'ROI för Solvalla avd 3 den 17 juli 2025' eller 'ROI 2025-07-17 S v75-1'.")
    public List<RoiDto> roiByPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) return List.of();
        String norm = normalize(phrase);

        Integer d = parseDateFromSwedish(norm);
        if (d == null) d = parseDateRelative(norm);
        if (d == null) d = parseMonthDayNoYear(norm);
        if (d == null) d = parseDateDigits(norm);

        String banKod = toBanKod(norm);
        String lap = parseLapOrAvdFlexible(norm);

        if (d == null || banKod == null || lap == null) return List.of();

        return roiRepo.fetchRoiByDateTrackLap(d, banKod, lap).stream()
                .map(this::toDto)
                .toList();
    }

    // ===== DTOs =====

    public record RoiDto( //Oförändrad struktur
                          String horseName,
                          Integer numberOfHorse,
                          String banKod,
                          String lap,
                          BigDecimal roiTotalt,
                          BigDecimal roiVinnare,
                          BigDecimal roiPlats,
                          BigDecimal roiTrio
    ) { }

    public static class RoiSummary {
        public String lap;
        public BigDecimal totalt = BigDecimal.ZERO;
        public BigDecimal vinnare = BigDecimal.ZERO;
        public BigDecimal plats = BigDecimal.ZERO;
        public BigDecimal trio = BigDecimal.ZERO;
    }

    private RoiSummary mapSummary(String lap, Object[] arr) {
        RoiSummary s = new RoiSummary();
        s.lap = lap;
        if (arr != null && arr.length == 3 && arr[0] instanceof BigDecimal a && arr[1] instanceof BigDecimal b && arr[2] instanceof BigDecimal c) {
            s.vinnare = a; s.plats = b; s.trio = c;
        }
        // Totalt (alla kategorier) räknas enklast separat via sumTotalt-queries om du vill;
        // här låter vi 'totalt' stå kvar som 0 och använder roi_total_by_date_track / sumTotaltByDateTrackLap när det behövs.
        return s;
    }

    // ===== Hjälpare =====

    private RoiDto toDto(Roi r) {
        var hr = r.getRank();
        return new RoiDto(
                hr != null ? hr.getNameOfHorse() : null,
                hr != null ? hr.getNumberOfHorse() : null,
                hr != null ? hr.getBanKod() : null,
                hr != null ? hr.getLap() : null,
                r.getRoiTotalt(),
                r.getRoiVinnare(),
                r.getRoiPlats(),
                r.getRoiTrio()
        );
    }

    private static BigDecimal metricValue(Roi r, String metric) {
        if (r == null) return BigDecimal.ZERO;
        return switch (metric) {
            case "vinnare" -> nz(r.getRoiVinnare());
            case "plats"   -> nz(r.getRoiPlats());
            case "trio"    -> nz(r.getRoiTrio());
            default        -> nz(r.getRoiTotalt());
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // === Datum/bana/lopp-parsning (utökad, i linje med TravTools) ===

    private Integer toIntDateFlexible(String date) {
        if (date == null || date.isBlank()) return null;
        String norm = normalize(date);

        Integer rel = parseDateRelative(norm);
        if (rel != null) return rel;

        Integer fromWords = parseDateFromSwedish(norm);
        if (fromWords != null) return fromWords;

        Integer md = parseMonthDayNoYear(norm);
        if (md != null) return md;

        return parseDateDigits(norm);
    }

    private Integer parseDateDigits(String norm) {
        try {
            Matcher ymd = Pattern.compile("(\\d{4})[-/ ]?(\\d{2})[-/ ]?(\\d{2})").matcher(norm);
            if (ymd.find()) {
                int year = Integer.parseInt(ymd.group(1));
                int month = Integer.parseInt(ymd.group(2));
                int day = Integer.parseInt(ymd.group(3));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveBanKodFlexible(String banKodOrTrack) {
        if (banKodOrTrack == null || banKodOrTrack.isBlank()) return null;
        String norm = normalize(banKodOrTrack);
        return toBanKod(norm);
    }

    private String parseLapFlexible(String lapOrPhrase) {
        if (lapOrPhrase == null || lapOrPhrase.isBlank()) return null;
        return parseLap(normalize(lapOrPhrase));
    }

    private String parseLapOrAvdFlexible(String s) {
        if (s == null || s.isBlank()) return null;
        String n = normalize(s);
        String avd = parseAvdFlexible(n);
        if (avd != null) return avd;
        return parseLap(n);
    }

    // ===== Parsers (svenska datum, avd, mm) =====

    private static final Map<String,Integer> svMonth = Map.ofEntries(
            Map.entry("januari",1), Map.entry("februari",2), Map.entry("mars",3), Map.entry("april",4),
            Map.entry("maj",5), Map.entry("juni",6), Map.entry("juli",7), Map.entry("augusti",8),
            Map.entry("september",9), Map.entry("oktober",10), Map.entry("november",11), Map.entry("december",12)
    );

    private static final Map<String, String> trackToBanKod = Map.ofEntries(
            Map.entry("Ar", "Arvika"),     Map.entry("Ax", "Axevalla"),
            Map.entry("B",  "Bergsåker"),  Map.entry("Bo", "Boden"),
            Map.entry("Bs", "Bollnäs"),    Map.entry("D",  "Dannero"),
            Map.entry("Dj", "Dala Järna"), Map.entry("E",  "Eskilstuna"),
            Map.entry("J",  "Jägersro"),   Map.entry("F",  "Färjestad"),
            Map.entry("G",  "Gävle"),      Map.entry("Gt", "Göteborg trav"),
            Map.entry("H",  "Hagmyren"),   Map.entry("Hd", "Halmstad"),
            Map.entry("Hg", "Hoting"),     Map.entry("Kh", "Karlshamn"),
            Map.entry("Kr", "Kalmar"),     Map.entry("L",  "Lindesberg"),
            Map.entry("Ly", "Lycksele"),   Map.entry("Mp", "Mantorp"),
            Map.entry("Ov", "Oviken"),     Map.entry("Ro", "Romme"),
            Map.entry("Rä", "Rättvik"),    Map.entry("S",  "Solvalla"),
            Map.entry("Sk", "Skellefteå"), Map.entry("Sä", "Solänget"),
            Map.entry("Ti", "Tingsryd"),   Map.entry("Tt", "Täby Trav"),
            Map.entry("U",  "Umåker"),     Map.entry("Vd", "Vemdalen"),
            Map.entry("Vg", "Vaggeryd"),   Map.entry("Vi", "Visby"),
            Map.entry("Å",  "Åby"),        Map.entry("Åm", "Åmål"),
            Map.entry("År", "Årjäng"),     Map.entry("Ö",  "Örebro"),
            Map.entry("Ös", "Östersund")
    );

    private static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static Integer parseDateFromSwedish(String norm) {
        if (norm == null) return null;
        // "2025 17 juli" (år dag månad)
        Matcher ydm = Pattern.compile("(\\d{4})\\D{0,5}(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)").matcher(norm);
        if (ydm.find()) {
            int year = Integer.parseInt(ydm.group(1));
            int day = Integer.parseInt(ydm.group(2));
            int month = svMonth.getOrDefault(ydm.group(3), 0);
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        // "17 juli 2025" (dag månad år)
        Matcher dmy = Pattern.compile("(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)\\D{0,5}(\\d{4})").matcher(norm);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = svMonth.getOrDefault(dmy.group(2), 0);
            int year = Integer.parseInt(dmy.group(3));
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        return null;
    }

    private static Integer parseDateRelative(String norm) {
        ZoneId tz = ZoneId.of("Europe/Stockholm");
        LocalDate base = LocalDate.now(tz);
        if (norm.contains("idag")) return base.getYear()*10000 + base.getMonthValue()*100 + base.getDayOfMonth();
        if (norm.contains("imorgon") || norm.contains("i morgon")) {
            LocalDate d = base.plusDays(1);
            return d.getYear()*10000 + d.getMonthValue()*100 + d.getDayOfMonth();
        }
        if (norm.contains("igar") || norm.contains("igår")) {
            LocalDate d = base.minusDays(1);
            return d.getYear()*10000 + d.getMonthValue()*100 + d.getDayOfMonth();
        }
        return null;
    }

    private static Integer parseMonthDayNoYear(String norm) {
        Matcher md = Pattern.compile("\\b(\\d{1,2})[-/ ](\\d{1,2})\\b").matcher(norm);
        if (md.find()) { // 2025 default
            int month = Integer.parseInt(md.group(1));
            int day = Integer.parseInt(md.group(2));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return 2025*10000 + month*100 + day;
        }
        return null;
    }

    private static String toBanKod(String norm) {
        if (norm == null) return null;
        // 1) angiven bankod
        for (String code : trackToBanKod.keySet()) {
            if (norm.matches(".*\\b" + normalize(code) + "\\b.*")) return code;
        }
        // 2) bannamn -> bankod
        for (Map.Entry<String,String> e : trackToBanKod.entrySet()) {
            if (norm.contains(normalize(e.getValue()))) return e.getKey();
        }
        return null;
    }

    private static String parseLap(String norm) {
        Matcher m = Pattern.compile("(?:lopp\\s*)?(\\d+)").matcher(norm);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String parseAvdFlexible(String norm) {
        Matcher m1 = Pattern.compile("\\bavd(elning)?\\s*(\\d{1,2})\\b").matcher(norm);
        if (m1.find()) return m1.group(2);
        Matcher m2 = Pattern.compile("\\b(v75|v86|gs75|v64|v65|dd|ld)[-: ]?(\\d{1,2})\\b").matcher(norm);
        if (m2.find()) return m2.group(2);
        return null;
    }
}
