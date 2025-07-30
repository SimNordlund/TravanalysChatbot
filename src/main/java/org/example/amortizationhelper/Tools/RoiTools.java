package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Roi;
import org.example.amortizationhelper.repo.RoiRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Objects;

@Component
@AllArgsConstructor
public class RoiTools {

    private final RoiRepo roiRepo;

    @Tool(name = "roi_total_by_date_track",
            description = "Summera ROI Totalt för alla hästar på ett datum och bana. Accepterar YYYYMMDD, YYYY-MM-DD eller svensk fras (t.ex. '17 juli 2025') samt bannamn (t.ex. 'Solvalla') eller bankod (t.ex. 'S').")
    public BigDecimal roiTotalByDateAndTrack(String date, String banKodOrTrack) {
        Integer d = toIntDateFlexible(date);
        if (d == null) return BigDecimal.ZERO;
        String normBan = resolveBanKodFlexible(banKodOrTrack);
        if (normBan == null) return BigDecimal.ZERO;
        return roiRepo.findByRank_StartDateAndRank_BanKod(d, normBan)
                .stream()
                .map(Roi::getRoiTotalt)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Tool(name = "roi_by_date_track_lap",
            description = "Hämta ROI (totalt/vinnare/plats/trio) för datum, bana och lopp. Accepterar svenska datum, bannamn/bankod samt 'lopp 5' eller bara '5'. Returnerar även hästnamn och startnummer.")
    public List<RoiDto> roiByDateTrackLap(String date, String banKodOrTrack, String lapOrPhrase) {
        Integer d = toIntDateFlexible(date);
        if (d == null) return List.of();
        String normBan = resolveBanKodFlexible(banKodOrTrack);
        if (normBan == null) return List.of();
        String lap = parseLapFlexible(lapOrPhrase);
        if (lap == null) return List.of();

        return roiRepo.fetchRoiByDateTrackLap(d, normBan, lap).stream()
                .map(this::toDto)
                .toList();
    }

    @Tool(name = "roi_by_phrase",
            description = "Tolka en svensk fras (datum/bana/lopp) och hämta ROI. Ex: 'ROI för Solvalla lopp 3 den 17 juli 2025' eller 'ROI 2025-07-17 S 3'.")
    public List<RoiDto> roiByPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) return List.of();
        String norm = normalize(phrase);

        Integer d = parseDateFromSwedish(norm);
        if (d == null) d = parseDateDigits(norm);

        String banKod = toBanKod(norm);
        String lap = parseLap(norm);

        if (d == null || banKod == null || lap == null) return List.of();

        return roiRepo.fetchRoiByDateTrackLap(d, banKod, lap).stream()
                .map(this::toDto)
                .toList();
    }

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

    private Integer toIntDateFlexible(String date) {
        if (date == null || date.isBlank()) return null;
        String norm = normalize(date);
        Integer fromWords = parseDateFromSwedish(norm);
        if (fromWords != null) return fromWords;
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

    // ELPERS jao

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

    // dto
    public record RoiDto(
            String horseName,
            Integer numberOfHorse,
            String banKod,
            String lap,
            BigDecimal roiTotalt,
            BigDecimal roiVinnare,
            BigDecimal roiPlats,
            BigDecimal roiTrio
    ) {
    }
}
