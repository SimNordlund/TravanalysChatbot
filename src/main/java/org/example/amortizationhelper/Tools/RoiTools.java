package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Roi;
import org.example.amortizationhelper.repo.RoiRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer; //Changed!
import java.util.*; //Changed!
import java.util.regex.Matcher; //Changed!
import java.util.regex.Pattern; //Changed!
import java.util.List;
import java.util.Objects;

@Component
@AllArgsConstructor
public class RoiTools {

    private final RoiRepo roiRepo;

    @Tool(name = "roi_total_by_date_track",
            description = "Summera ROI Totalt för alla hästar på ett datum och bana. Accepterar YYYYMMDD, YYYY-MM-DD eller svensk fras (t.ex. '17 juli 2025') samt bannamn (t.ex. 'Solvalla') eller bankod (t.ex. 'S').") //Changed!
    public BigDecimal roiTotalByDateAndTrack(String date, String banKodOrTrack) { //Changed!
        Integer d = toIntDateFlexible(date); //Changed!
        if (d == null) return BigDecimal.ZERO;
        String normBan = resolveBanKodFlexible(banKodOrTrack); //Changed!
        if (normBan == null) return BigDecimal.ZERO; //Changed!
        return roiRepo.findByRank_StartDateAndRank_BanKod(d, normBan)
                .stream()
                .map(Roi::getRoiTotalt)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Tool(name = "roi_by_date_track_lap",
            description = "Hämta ROI (totalt/vinnare/plats/trio) för datum, bana och lopp. Accepterar svenska datum, bannamn/bankod samt 'lopp 5' eller bara '5'. Returnerar även hästnamn och startnummer.") //Changed!
    public List<RoiDto> roiByDateTrackLap(String date, String banKodOrTrack, String lapOrPhrase) { //Changed!
        Integer d = toIntDateFlexible(date); //Changed!
        if (d == null) return List.of();
        String normBan = resolveBanKodFlexible(banKodOrTrack); //Changed!
        if (normBan == null) return List.of(); //Changed!
        String lap = parseLapFlexible(lapOrPhrase); //Changed!
        if (lap == null) return List.of(); //Changed!

        return roiRepo.fetchRoiByDateTrackLap(d, normBan, lap).stream()
                .map(this::toDto)
                .toList();
    }

    // Ny tool: helt fri svensk fras, t.ex. "ROI för Solvalla lopp 3 den 17 juli 2025" //Changed!
    @Tool(name = "roi_by_phrase", //Changed!
            description = "Tolka en svensk fras (datum/bana/lopp) och hämta ROI. Ex: 'ROI för Solvalla lopp 3 den 17 juli 2025' eller 'ROI 2025-07-17 S 3'.") //Changed!
    public List<RoiDto> roiByPhrase(String phrase) { //Changed!
        if (phrase == null || phrase.isBlank()) return List.of(); //Changed!
        String norm = normalize(phrase); //Changed!

        Integer d = parseDateFromSwedish(norm); //Changed!
        if (d == null) d = parseDateDigits(norm); //Changed!

        String banKod = toBanKod(norm); //Changed!
        String lap = parseLap(norm); //Changed!

        if (d == null || banKod == null || lap == null) return List.of(); //Changed!

        return roiRepo.fetchRoiByDateTrackLap(d, banKod, lap).stream() //Changed!
                .map(this::toDto) //Changed!
                .toList(); //Changed!
    } //Changed!

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

    // === Flexibla parsers (datum, bana, lopp) === //Changed!

    private Integer toIntDateFlexible(String date) { //Changed!
        if (date == null || date.isBlank()) return null; //Changed!
        String norm = normalize(date); //Changed!
        Integer fromWords = parseDateFromSwedish(norm); //Changed!
        if (fromWords != null) return fromWords; //Changed!
        return parseDateDigits(norm); //Changed!
    } //Changed!

    private Integer parseDateDigits(String norm) { //Changed!
        try {
            Matcher ymd = Pattern.compile("(\\d{4})[-/ ]?(\\d{2})[-/ ]?(\\d{2})").matcher(norm); //Changed!
            if (ymd.find()) { //Changed!
                int year = Integer.parseInt(ymd.group(1)); //Changed!
                int month = Integer.parseInt(ymd.group(2)); //Changed!
                int day = Integer.parseInt(ymd.group(3)); //Changed!
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return year*10000 + month*100 + day; //Changed!
            } //Changed!
        } catch (Exception ignored) {} //Changed!
        return null; //Changed!
    } //Changed!

    private String resolveBanKodFlexible(String banKodOrTrack) { //Changed!
        if (banKodOrTrack == null || banKodOrTrack.isBlank()) return null; //Changed!
        String norm = normalize(banKodOrTrack); //Changed!
        return toBanKod(norm); //Changed!
    } //Changed!

    private String parseLapFlexible(String lapOrPhrase) { //Changed!
        if (lapOrPhrase == null || lapOrPhrase.isBlank()) return null; //Changed!
        return parseLap(normalize(lapOrPhrase)); //Changed!
    } //Changed!

    // ——— Helpers delade med TravTools-lik logik ——— //Changed!

    private static final Map<String,Integer> svMonth = Map.ofEntries( //Changed!
            Map.entry("januari",1), Map.entry("februari",2), Map.entry("mars",3), Map.entry("april",4), //Changed!
            Map.entry("maj",5), Map.entry("juni",6), Map.entry("juli",7), Map.entry("augusti",8), //Changed!
            Map.entry("september",9), Map.entry("oktober",10), Map.entry("november",11), Map.entry("december",12) //Changed!
    ); //Changed!

    private static final Map<String, String> trackToBanKod = Map.ofEntries( //Changed!
            Map.entry("Ar", "Arvika"),     Map.entry("Ax", "Axevalla"), //Changed!
            Map.entry("B",  "Bergsåker"),  Map.entry("Bo", "Boden"), //Changed!
            Map.entry("Bs", "Bollnäs"),    Map.entry("D",  "Dannero"), //Changed!
            Map.entry("Dj", "Dala Järna"), Map.entry("E",  "Eskilstuna"), //Changed!
            Map.entry("J",  "Jägersro"),   Map.entry("F",  "Färjestad"), //Changed!
            Map.entry("G",  "Gävle"),      Map.entry("Gt", "Göteborg trav"), //Changed!
            Map.entry("H",  "Hagmyren"),   Map.entry("Hd", "Halmstad"), //Changed!
            Map.entry("Hg", "Hoting"),     Map.entry("Kh", "Karlshamn"), //Changed!
            Map.entry("Kr", "Kalmar"),     Map.entry("L",  "Lindesberg"), //Changed!
            Map.entry("Ly", "Lycksele"),   Map.entry("Mp", "Mantorp"), //Changed!
            Map.entry("Ov", "Oviken"),     Map.entry("Ro", "Romme"), //Changed!
            Map.entry("Rä", "Rättvik"),    Map.entry("S",  "Solvalla"), //Changed!
            Map.entry("Sk", "Skellefteå"), Map.entry("Sä", "Solänget"), //Changed!
            Map.entry("Ti", "Tingsryd"),   Map.entry("Tt", "Täby Trav"), //Changed!
            Map.entry("U",  "Umåker"),     Map.entry("Vd", "Vemdalen"), //Changed!
            Map.entry("Vg", "Vaggeryd"),   Map.entry("Vi", "Visby"), //Changed!
            Map.entry("Å",  "Åby"),        Map.entry("Åm", "Åmål"), //Changed!
            Map.entry("År", "Årjäng"),     Map.entry("Ö",  "Örebro"), //Changed!
            Map.entry("Ös", "Östersund") //Changed!
    ); //Changed!

    private static String normalize(String s) { //Changed!
        if (s == null) return null; //Changed!
        String n = Normalizer.normalize(s, Normalizer.Form.NFD) //Changed!
                .replaceAll("\\p{M}+", ""); // ta bort diakritik //Changed!
        return n.toLowerCase(Locale.ROOT).trim(); //Changed!
    } //Changed!

    private static Integer parseDateFromSwedish(String norm) { //Changed!
        if (norm == null) return null; //Changed!
        // "2025 17 juli" (år dag månad) //Changed!
        Matcher ydm = Pattern.compile("(\\d{4})\\D{0,5}(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)").matcher(norm); //Changed!
        if (ydm.find()) { //Changed!
            int year = Integer.parseInt(ydm.group(1)); //Changed!
            int day = Integer.parseInt(ydm.group(2)); //Changed!
            int month = svMonth.getOrDefault(ydm.group(3), 0); //Changed!
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day; //Changed!
        } //Changed!
        // "17 juli 2025" (dag månad år) //Changed!
        Matcher dmy = Pattern.compile("(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)\\D{0,5}(\\d{4})").matcher(norm); //Changed!
        if (dmy.find()) { //Changed!
            int day = Integer.parseInt(dmy.group(1)); //Changed!
            int month = svMonth.getOrDefault(dmy.group(2), 0); //Changed!
            int year = Integer.parseInt(dmy.group(3)); //Changed!
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day; //Changed!
        } //Changed!
        return null; //Changed!
    } //Changed!

    private static String toBanKod(String norm) { //Changed!
        if (norm == null) return null; //Changed!
        // 1) angiven bankod //Changed!
        for (String code : trackToBanKod.keySet()) { //Changed!
            if (norm.matches(".*\\b" + normalize(code) + "\\b.*")) return code; //Changed!
        } //Changed!
        // 2) bannamn -> bankod //Changed!
        for (Map.Entry<String,String> e : trackToBanKod.entrySet()) { //Changed!
            if (norm.contains(normalize(e.getValue()))) return e.getKey(); //Changed!
        } //Changed!
        return null; //Changed!
    } //Changed!

    private static String parseLap(String norm) { //Changed!
        Matcher m = Pattern.compile("(?:lopp\\s*)?(\\d+)").matcher(norm); //Changed!
        if (m.find()) return m.group(1); //Changed!
        return null; //Changed!
    } //Changed!

    // === Behåller din DTO oförändrad ===
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
