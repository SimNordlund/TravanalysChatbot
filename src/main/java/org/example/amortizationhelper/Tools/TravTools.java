package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.HorseResult;
import org.example.amortizationhelper.repo.HorseResultRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.Normalizer; //Changed!
import java.util.*; //Changed!
import java.util.regex.Matcher; //Changed!
import java.util.regex.Pattern; //Changed!

@Component
@AllArgsConstructor
public class TravTools {

    private final HorseResultRepo horseResultRepo;

    @Tool(description = "Hämta värden om hästar baserat på ett id.")
    public HorseResult getHorseValues(Long id) {
        return horseResultRepo.findById(id).orElse(null);
    }

    @Tool(description = "Lista odds/värden för ett datum och en bana. Accepterar svenska datum (t.ex. '17 juli 2025') och bannamn (t.ex. 'Solvalla') eller bankod (t.ex. 'S').") //Changed!
    public List<HorseResult> listByDateAndTrackFlexible(String dateOrPhrase, String banKodOrTrack) { //Changed!
        Integer start = parseDateFlexible(dateOrPhrase); //Changed!
        String banKod = resolveBanKodFlexible(banKodOrTrack); //Changed!
        if (start == null || banKod == null) return List.of(); //Changed!

        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKod(start, banKod);
        System.out.println("Tool listByDateAndTrackFlexible hittade " + results.size() + " rader (date=" + start + ", banKod=" + banKod + ")"); //Changed!
        return results;
    }

    @Tool(name = "results_by_date_track_lap",
            description = "Oddsen/värden (Analys/Prestation/Motstånd/Tid) för datum, bana och lopp. Accepterar svenska datum, bannamn/bankod och t.ex. 'lopp 5' eller '5'.") //Changed!
    public List<HorseResult> listResultsByDateAndTrackAndLap(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) { //Changed!
        Integer startDate = parseDateFlexible(dateOrPhrase); //Changed!
        String banKod = resolveBanKodFlexible(banKodOrTrack); //Changed!
        String lap = parseLapFlexible(lapOrPhrase); //Changed!
        if (startDate == null || banKod == null || lap == null) return List.of(); //Changed!

        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKodAndLap(startDate, banKod, lap);
        System.out.println("Tool listByDateAndTrackAndLap hittade " + results.size() + " rader (date=" + startDate + ", banKod=" + banKod + ", lap=" + lap + ")"); //Changed!
        return results;
    }

    @Tool(description = "Sök fram en häst och dess värden baserat på namnet på hästen")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        return horseResultRepo.findByNameOfHorseContainingIgnoreCase(nameFragment);
    }

    @Tool(description = "Hämta topp N hästar (Analys) för datum, bana och lopp. Accepterar naturliga indata som svensk fras.") //Changed!
    public List<HorseResult> topHorses(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase, Integer limit) { //Changed!
        if (limit == null || limit <= 0) limit = 3;
        List<HorseResult> all = listResultsByDateAndTrackAndLap(dateOrPhrase, banKodOrTrack, lapOrPhrase); //Changed!
        return all.stream()
                .sorted((a,b) -> parse(b.getProcentAnalys()) - parse(a.getProcentAnalys()))
                .limit(limit)
                .toList();
    }

    @Tool(description = "Visa en hästs Travanalys odds och värden sorterade efter datum (senaste först).")
    public List<HorseResult> horseHistory(String nameFragment, Integer limit) {
        if (limit == null || limit <= 0) limit = 5;
        return horseResultRepo
                .findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(nameFragment)
                .stream()
                .limit(limit)
                .toList();
    }

    // === Naturligt språk: "Visa alla speltips för 2025 17 juli på Solvalla i lopp 5" ===
    @Tool(name = "find_tips_by_swedish_phrase",
            description = "Tolka svensk fras med datum, bana och lopp. Ex: 'Visa alla speltips för 2025 17 juli på Solvalla i lopp 5'. Stödjer även 'speltips 1'.")
    public List<HorseResult> findTipsBySwedishPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) return List.of();

        String norm = normalize(phrase);
        Integer date = parseDateFromSwedish(norm);
        String banKod = toBanKod(norm);
        String lap = parseLap(norm);

        if (date == null || banKod == null || lap == null) {
            System.out.println("find_tips_by_swedish_phrase kunde inte tolka alla fält");
            return List.of();
        }

        Integer tipsValue = parseExplicitTipsValue(norm);
        boolean wantAllTips = containsAllTips(norm);

        if (tipsValue != null) {
            List<HorseResult> rows =
                    horseResultRepo.findByStartDateAndBanKodAndLapAndTips(date, banKod, lap, tipsValue);
            System.out.println("Tool find_tips_by_swedish_phrase hittade " + rows.size() + " rader med tips=" + tipsValue);
            return rows;
        }

        if (wantAllTips) {
            List<HorseResult> rows =
                    horseResultRepo.findByStartDateAndBanKodAndLapAndTipsIsNotNull(date, banKod, lap);
            System.out.println("Tool find_tips_by_swedish_phrase hittade " + rows.size() + " rader med tips IS NOT NULL");
            return rows;
        }

        // default: visa allt i loppet, men filtrera på tips != null för att matcha “speltips”
        List<HorseResult> all = horseResultRepo.findByStartDateAndBanKodAndLap(date, banKod, lap);
        List<HorseResult> filtered = all.stream().filter(r -> r.getTips() != null).toList();
        System.out.println("Tool find_tips_by_swedish_phrase hittade " + filtered.size() + " rader (filtrerat på tips != null)");
        return filtered;
    }

    // ==== Hjälpare ====
    private int parse(String val) {
        try { return Integer.parseInt(val); } catch (Exception e) { return -1; }
    }

    // Kod -> ban-namn (befintlig)
    private static final Map<String, String> tackToBanKod = Map.ofEntries(
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

    // Svenska månadsnamn -> månad 1..12
    private static final Map<String,Integer> svMonth = Map.ofEntries(
            Map.entry("januari",1), Map.entry("februari",2), Map.entry("mars",3), Map.entry("april",4),
            Map.entry("maj",5), Map.entry("juni",6), Map.entry("juli",7), Map.entry("augusti",8),
            Map.entry("september",9), Map.entry("oktober",10), Map.entry("november",11), Map.entry("december",12)
    );

    private static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", ""); // ta bort diakritik
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean containsAllTips(String norm) {
        return norm.contains("alla speltips") || norm.contains("alla tips");
    }

    private static Integer parseExplicitTipsValue(String norm) {
        Matcher m = Pattern.compile("(speltips|tips)\\s*(=|ar|:)?\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(norm);
        if (m.find()) {
            try { return Integer.parseInt(m.group(3)); } catch (Exception ignored) {}
        }
        return null;
    }

    // Utökad: fångar både "lopp 5" och bara "5" //Changed!
    private static String parseLap(String norm) { //Changed!
        Matcher m = Pattern.compile("(?:lopp\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(norm); //Changed!
        if (m.find()) return m.group(1); //Changed!
        return null; //Changed!
    } //Changed!

    // === Nya flex-hjälpare som återanvänds i fler metoder ===
    private static Integer parseDateFlexible(String anyDate) { //Changed!
        if (anyDate == null || anyDate.isBlank()) return null; //Changed!
        String norm = normalize(anyDate); //Changed!
        Integer fromWords = parseDateFromSwedish(norm); //Changed!
        if (fromWords != null) return fromWords; //Changed!
        // Fallback: YYYY-MM-DD, YYYY/MM/DD, YYYY MM DD eller YYYYMMDD //Changed!
        Matcher ymdDigits = Pattern.compile("(\\d{4})[-/ ]?(\\d{2})[-/ ]?(\\d{2})").matcher(norm); //Changed!
        if (ymdDigits.find()) { //Changed!
            int year = Integer.parseInt(ymdDigits.group(1)); //Changed!
            int month = Integer.parseInt(ymdDigits.group(2)); //Changed!
            int day = Integer.parseInt(ymdDigits.group(3)); //Changed!
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return year*10000 + month*100 + day; //Changed!
        } //Changed!
        return null; //Changed!
    } //Changed!

    private static String parseLapFlexible(String lapOrPhrase) { //Changed!
        if (lapOrPhrase == null || lapOrPhrase.isBlank()) return null; //Changed!
        return parseLap(normalize(lapOrPhrase)); //Changed!
    } //Changed!

    private static String resolveBanKodFlexible(String banKodOrTrack) { //Changed!
        if (banKodOrTrack == null || banKodOrTrack.isBlank()) return null; //Changed!
        String norm = normalize(banKodOrTrack); //Changed!
        return toBanKod(norm); //Changed!
    } //Changed!

    // === Datumparser för svenska fraser ===
    private static Integer parseDateFromSwedish(String norm) {
        if (norm == null) return null;
        // Ex 1: "2025 17 juli" (år dag månad)
        Matcher ydm = Pattern.compile("(\\d{4})\\D{0,5}(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)").matcher(norm);
        if (ydm.find()) {
            int year = Integer.parseInt(ydm.group(1));
            int day = Integer.parseInt(ydm.group(2));
            int month = svMonth.getOrDefault(ydm.group(3), 0);
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        // Ex 2: "17 juli 2025" (dag månad år)
        Matcher dmy = Pattern.compile("(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)\\D{0,5}(\\d{4})").matcher(norm);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = svMonth.getOrDefault(dmy.group(2), 0);
            int year = Integer.parseInt(dmy.group(3));
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        // Ex 3: "2025-07-17" eller "20250717"
        Matcher ymdDigits = Pattern.compile("(\\d{4})[-/ ]?(\\d{2})[-/ ]?(\\d{2})").matcher(norm);
        if (ymdDigits.find()) {
            int year = Integer.parseInt(ymdDigits.group(1));
            int month = Integer.parseInt(ymdDigits.group(2));
            int day = Integer.parseInt(ymdDigits.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        return null;
    }

    private static String toBanKod(String norm) {
        if (norm == null) return null;
        // 1) om användaren anger bankod direkt (t.ex. "S")
        for (String code : tackToBanKod.keySet()) {
            if (norm.matches(".*\\b" + normalize(code) + "\\b.*")) return code;
        }
        // 2) fullständigt namn, t.ex. "Solvalla" -> "S"
        for (Map.Entry<String,String> e : tackToBanKod.entrySet()) {
            if (norm.contains(normalize(e.getValue()))) return e.getKey();
        }
        return null;
    }
}
