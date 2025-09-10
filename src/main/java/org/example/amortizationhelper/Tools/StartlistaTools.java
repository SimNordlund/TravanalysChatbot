package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Startlista;
import org.example.amortizationhelper.repo.StartlistaRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
public class StartlistaTools {

    private final StartlistaRepo startlistaRepo;

    // ==== BEFINTLIGA METODER ====

    @Tool(description = "Värden för en häst som tillhör en startlista")
    public Startlista getStartlistaValue(Long id) {
        return startlistaRepo.findById(id).orElse(null);
    }

    @Tool(
            name = "startlista_by_date_track_lap",
            description = "Lista Startlista (kusk/spår/distans) för datum (YYYYMMDD/YYYY-MM-DD), bankod och lopp."
    )
    public List<Startlista> findStartListaByStartDateAndBanKodAndLap(String date, String banKod, String lap) {
        String cleaned = date.replaceAll("-", "");
        Integer startDate;
        try { startDate = Integer.valueOf(cleaned); } catch (NumberFormatException e) { return List.of(); }

        String normBanKod = banKod == null ? null : banKod.trim().toUpperCase();
        Integer lapInt;
        try { lapInt = Integer.valueOf(lap.trim()); } catch (Exception e) { lapInt = null; }

        List<Startlista> results =
                startlistaRepo.findByStartDateAndBanKodAndLap(startDate, normBanKod, lapInt);
        System.out.println("[startlista_by_date_track_lap] d=" + startDate + " bana=" + normBanKod + " lap=" + lapInt + " -> " + results.size());
        return results;
    }

    // ==== NY FUNKTIONALITET (FLEX-PARSNING, DISCOVERY, SORTERADE FÄLT) ====

    // --- Discovery (för att guida användaren) ---

    @Tool(name = "start_dates_all", description = "Lista alla datum som finns i Startlista (senaste först).")
    public List<Integer> startDatesAll() {
        return startlistaRepo.distinctDatesAll();
    }

    @Tool(name = "start_tracks_by_date", description = "Lista bankoder på ett givet datum (från Startlista).")
    public List<String> startTracksByDate(String dateOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        if (d == null) return List.of();
        return startlistaRepo.distinctBanKodByDate(d);
    }

    @Tool(name = "start_laps_by_date_track", description = "Lista lopp/avdelningar för ett datum + bana.")
    public List<Integer> startLapsByDateTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return startlistaRepo.distinctLapsByDateAndBanKod(d, b);
    }

    @Tool(name = "start_kuskar_by_date_track_lap", description = "Lista kuskar för datum + bana + lopp.")
    public List<String> kuskarByDateTrackLap(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        Integer lap = parseLapFlexibleToInt(lapOrPhrase);
        if (d == null || b == null || lap == null) return List.of();
        return startlistaRepo.distinctKuskByDateTrackLap(d, b, lap);
    }

    @Tool(name = "start_distans_by_date_track", description = "Lista distanser för datum + bana.")
    public List<Integer> distansByDateTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return startlistaRepo.distinctDistansByDateTrack(d, b);
    }

    // --- Fält/Startlista hämtning ---

    @Tool(name = "start_field_sorted", description = "Hämta hela startfältet (datum + bana + lopp) sorterat på startnummer (nr).")
    public List<Startlista> startFieldSorted(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        Integer lap = parseLapFlexibleToInt(lapOrPhrase);
        if (d == null || b == null || lap == null) return List.of();
        return startlistaRepo.findByStartDateAndBanKodAndLapOrderByNumberOfHorseAsc(d, b, lap);
    }

    @Tool(name = "start_field_for_track", description = "Hämta alla startlistor för datum + bana, sorterat (lopp↑, nr↑).")
    public List<Startlista> startFieldForTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return startlistaRepo.findByStartDateAndBanKodOrderByLapAscNumberOfHorseAsc(d, b);
    }

    @Tool(name = "start_search_horse", description = "Sök häst i startlistan för datum + bana (+ ev. lopp) via namnfragment.")
    public List<Startlista> searchHorseInStartlista(String dateOrPhrase, String banKodOrTrack, String nameFragment, String lapOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null || nameFragment == null || nameFragment.isBlank()) return List.of();
        Integer lap = parseLapFlexibleToInt(lapOrNull);
        if (lap == null) {
            // Filtrera per datum+bana och sök i minnet (enkelt alternativ)
            List<Startlista> base = startlistaRepo.findByStartDateAndBanKod(d, b);
            String nf = normalize(nameFragment);
            List<Startlista> out = new ArrayList<>();
            for (Startlista s : base) {
                if (normalize(s.getNameOfHorse()).contains(nf)) out.add(s);
            }
            out.sort(Comparator.comparing(Startlista::getLap).thenComparing(Startlista::getNumberOfHorse));
            return out;
        } else {
            return startlistaRepo.findByStartDateAndBanKodAndLapAndNameOfHorseContainingIgnoreCase(
                    d, b, lap, nameFragment);
        }
    }

    @Tool(name = "start_get_by_number", description = "Hämta en specifik start baserat på datum + bana + lopp + startnummer (nr).")
    public Startlista getStartByNumber(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase, Integer number) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        Integer lap = parseLapFlexibleToInt(lapOrPhrase);
        if (d == null || b == null || lap == null || number == null) return null;
        return startlistaRepo.findFirstByStartDateAndBanKodAndLapAndNumberOfHorse(d, b, lap, number).orElse(null);
    }

    @Tool(name = "start_grid", description = "Returnera ett lättviktigt rutnät för UI: nr, namn, kusk, spår, distans, lopp.")
    public List<StartRow> startGrid(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        List<Startlista> rows = startFieldSorted(dateOrPhrase, banKodOrTrack, lapOrPhrase);
        List<StartRow> out = new ArrayList<>();
        for (Startlista s : rows) {
            out.add(new StartRow(
                    s.getNumberOfHorse(), s.getNameOfHorse(),
                    s.getKusk(), s.getSpar(), s.getDistans(), s.getLap()
            ));
        }
        return out;
    }

    // ==== HJÄLPKLASSER (DTO) ====

    public static class StartRow {
        public Integer nr;
        public String namn;
        public String kusk;
        public Integer spar;
        public Integer distans;
        public Integer lopp;
        public StartRow(Integer nr, String namn, String kusk, Integer spar, Integer distans, Integer lopp) {
            this.nr = nr; this.namn = namn; this.kusk = kusk; this.spar = spar; this.distans = distans; this.lopp = lopp;
        }
    }

    // ==== PARSING & NORMALISERING (lokalt, oberoende av TravTools) ====

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

    private static Integer parseDateRelative(String norm) {
        ZoneId tz = ZoneId.of("Europe/Stockholm");
        LocalDate base = LocalDate.now(tz);
        if (norm.contains("idag")) return yyyymmdd(base);
        if (norm.contains("imorgon") || norm.contains("i morgon")) return yyyymmdd(base.plusDays(1));
        if (norm.contains("igar") || norm.contains("igår")) return yyyymmdd(base.minusDays(1));
        return null;
    }

    private static Integer yyyymmdd(LocalDate d) {
        return d.getYear()*10000 + d.getMonthValue()*100 + d.getDayOfMonth();
    }

    private static final Map<String,Integer> svMonth = Map.ofEntries(
            Map.entry("januari",1), Map.entry("februari",2), Map.entry("mars",3), Map.entry("april",4),
            Map.entry("maj",5), Map.entry("juni",6), Map.entry("juli",7), Map.entry("augusti",8),
            Map.entry("september",9), Map.entry("oktober",10), Map.entry("november",11), Map.entry("december",12)
    );

    private static Integer parseDateFromSwedish(String norm) {
        if (norm == null) return null;
        Matcher ydm = Pattern.compile("(\\d{4})\\D{0,5}(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)").matcher(norm);
        if (ydm.find()) {
            int year = Integer.parseInt(ydm.group(1));
            int day = Integer.parseInt(ydm.group(2));
            int month = svMonth.getOrDefault(ydm.group(3), 0);
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        Matcher dmy = Pattern.compile("(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)\\D{0,5}(\\d{4})").matcher(norm);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = svMonth.getOrDefault(dmy.group(2), 0);
            int year = Integer.parseInt(dmy.group(3));
            if (month >= 1 && day >= 1 && day <= 31) return year*10000 + month*100 + day;
        }
        return null;
    }

    private static Integer parseDateFlexible(String anyDate) {
        if (anyDate == null || anyDate.isBlank()) return null;
        String norm = normalize(anyDate);
        Integer rel = parseDateRelative(norm);
        if (rel != null) return rel;
        Integer fromWords = parseDateFromSwedish(norm);
        if (fromWords != null) return fromWords;
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
        for (String code : trackToBanKod.keySet()) {
            if (norm.matches(".*\\b" + normalize(code) + "\\b.*")) return code;
        }
        for (Map.Entry<String,String> e : trackToBanKod.entrySet()) {
            if (norm.contains(normalize(e.getValue()))) return e.getKey();
        }
        return null;
    }

    private static String resolveBanKodFlexible(String banKodOrTrack) {
        if (banKodOrTrack == null || banKodOrTrack.isBlank()) return null;
        String norm = normalize(banKodOrTrack);
        return toBanKod(norm);
    }

    private static Integer parseLapFlexibleToInt(String lapOrPhrase) {
        if (lapOrPhrase == null || lapOrPhrase.isBlank()) return null;
        String norm = normalize(lapOrPhrase);
        Matcher m1 = Pattern.compile("\\b(lopp|avd(elning)?)\\s*(\\d{1,2})\\b").matcher(norm);
        if (m1.find()) return Integer.parseInt(m1.group(3));
        Matcher m2 = Pattern.compile("\\b(v75|v86|gs75|v64|v65|dd|ld)[-: ]?(\\d{1,2})\\b").matcher(norm);
        if (m2.find()) return Integer.parseInt(m2.group(2));
        Matcher m3 = Pattern.compile("(\\d{1,2})(?!\\d)").matcher(norm);
        String last = null;
        while (m3.find()) last = m3.group(1);
        return last == null ? null : Integer.parseInt(last);
    }
}
