package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.HorseResult;
import org.example.amortizationhelper.repo.HorseResultRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class TravTools {

    private static final Map<String, String> tackToBanKod = Map.ofEntries(
            Map.entry("Ar", "Arvika"), Map.entry("Ax", "Axevalla"),
            Map.entry("B", "Bergsåker"), Map.entry("Bo", "Boden"),
            Map.entry("Bs", "Bollnäs"), Map.entry("D", "Dannero"),
            Map.entry("Dj", "Dala Järna"), Map.entry("E", "Eskilstuna"),
            Map.entry("J", "Jägersro"), Map.entry("F", "Färjestad"),
            Map.entry("G", "Gävle"), Map.entry("Gt", "Göteborg trav"),
            Map.entry("H", "Hagmyren"), Map.entry("Hd", "Halmstad"),
            Map.entry("Hg", "Hoting"), Map.entry("Kh", "Karlshamn"),
            Map.entry("Kr", "Kalmar"), Map.entry("L", "Lindesberg"),
            Map.entry("Ly", "Lycksele"), Map.entry("Mp", "Mantorp"),
            Map.entry("Ov", "Oviken"), Map.entry("Ro", "Romme"),
            Map.entry("Rä", "Rättvik"), Map.entry("S", "Solvalla"),
            Map.entry("Sk", "Skellefteå"), Map.entry("Sä", "Solänget"),
            Map.entry("Ti", "Tingsryd"), Map.entry("Tt", "Täby Trav"),
            Map.entry("U", "Umåker"), Map.entry("Vd", "Vemdalen"),
            Map.entry("Vg", "Vaggeryd"), Map.entry("Vi", "Visby"),
            Map.entry("Å", "Åby"), Map.entry("Åm", "Åmål"),
            Map.entry("År", "Årjäng"), Map.entry("Ö", "Örebro"),
            Map.entry("Ös", "Östersund")
    );

    private static final Map<String, Integer> svMonth = Map.ofEntries(
            Map.entry("januari", 1), Map.entry("februari", 2), Map.entry("mars", 3), Map.entry("april", 4),
            Map.entry("maj", 5), Map.entry("juni", 6), Map.entry("juli", 7), Map.entry("augusti", 8),
            Map.entry("september", 9), Map.entry("oktober", 10), Map.entry("november", 11), Map.entry("december", 12)
    );

    private final HorseResultRepo horseResultRepo;


    @Tool(description = "Hämta värden om hästar baserat på ett id.")
    public HorseResult getHorseValues(Long id) {
        return horseResultRepo.findById(id).orElse(null);
    }

    @Tool(description = "Lista odds/värden för ett datum och en bana. Accepterar svenska datum (t.ex. '17 juli 2025') och bannamn (t.ex. 'Solvalla') eller bankod (t.ex. 'S').")
    public List<HorseResult> listByDateAndTrackFlexible(String dateOrPhrase, String banKodOrTrack) {
        Integer start = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        if (start == null || banKod == null) return List.of();

        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKod(start, banKod);
        System.out.println("Tool listByDateAndTrackFlexible hittade " + results.size() + " rader (date=" + start + ", banKod=" + banKod + ")");
        return results;
    }

    @Tool(name = "results_by_date_track_lap",
            description = "Oddsen/värden (Analys/Prestation/Motstånd/Tid) för datum, bana och lopp. Accepterar svenska datum, bannamn/bankod och t.ex. 'lopp 5' eller '5'.")
    public List<HorseResult> listResultsByDateAndTrackAndLap(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapFlexible(lapOrPhrase);
        if (startDate == null || banKod == null || lap == null) return List.of();

        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKodAndLap(startDate, banKod, lap);
        System.out.println("Tool listByDateAndTrackAndLap hittade " + results.size() + " rader (date=" + startDate + ", banKod=" + banKod + ", lap=" + lap + ")");
        return results;
    }

    @Tool(description = "Hämta topp N hästar (Analys) för datum, bana och lopp. Accepterar naturliga indata som svensk fras.")
    public List<HorseResult> topHorses(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase, Integer limit) {
        if (limit == null || limit <= 0) limit = 3;
        List<HorseResult> all = listResultsByDateAndTrackAndLap(dateOrPhrase, banKodOrTrack, lapOrPhrase);
        return all.stream()
                .sorted((a, b) -> parse(b.getProcentAnalys()) - parse(a.getProcentAnalys()))
                .limit(limit)
                .toList();
    }

    @Tool(name = "pick_winner_across_starters",
            description = "Välj vinnare genom att väga ihop alla tillgängliga 'starter'-fönster för datum, bana, spelform och lopp. Returnerar topp 3 med motivering.")
    public List<WinnerSuggestion> pickWinnerAcrossStarters(String dateOrPhrase,
                                                           String banKodOrTrack,
                                                           String lapOrPhrase,
                                                           String spelFormOrPhrase,
                                                           Integer topN) {
        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapFlexible(lapOrPhrase);
        String parsedForm = parseSpelFormFlexible(spelFormOrPhrase);
        if (topN == null || topN <= 0) topN = 3;

        if (startDate == null || banKod == null || lap == null) return List.of();

        boolean aggregator = isAggregatorSpelForm(parsedForm);
        String effectiveForm = (parsedForm == null ? "vinnare" : parsedForm);


        List<HorseResult> rows = horseResultRepo
                .findByStartDateAndBanKodAndLapAndSpelFormIgnoreCase(startDate, banKod, lap, effectiveForm);


        if (rows.isEmpty() || aggregator) {
            rows = horseResultRepo.findByStartDateAndBanKodAndLapAndSpelFormIgnoreCase(startDate, banKod, lap, "vinnare");
            effectiveForm = "vinnare";
        }

        if (rows.isEmpty()) {
            rows = horseResultRepo.findByStartDateAndBanKodAndLap(startDate, banKod, lap);
            // Behåll effectiveForm="vinnare" för rapportering
        }

        if (rows.isEmpty()) return List.of();

        final String formForReturn = effectiveForm;

        Map<String, List<HorseResult>> byHorse = rows.stream()
                .collect(Collectors.groupingBy(HorseResult::getNameOfHorse));

        List<WinnerSuggestion> ranked = byHorse.entrySet().stream().map(e -> {
                    String name = e.getKey();
                    List<HorseResult> list = e.getValue();

                    List<Integer> starters = new ArrayList<>();
                    List<Integer> analys = new ArrayList<>();
                    List<Integer> prest = new ArrayList<>();
                    List<Integer> tid = new ArrayList<>();
                    List<Integer> motst = new ArrayList<>();

                    for (HorseResult r : list) {
                        int s = safeInt(r.getStarter());
                        starters.add(s);
                        analys.add(safeInt(r.getProcentAnalys()));
                        prest.add(safeInt(r.getProcentPrestation()));
                        tid.add(safeInt(r.getProcentFart()));
                        motst.add(safeInt(r.getProcentMotstand()));
                    }

                    double sumW = 0, sumAnalys = 0, sumPrest = 0, sumTid = 0, sumMot = 0;
                    for (int i = 0; i < starters.size(); i++) {
                        double w = Math.sqrt(Math.max(1, starters.get(i)));
                        sumW += w;
                        sumAnalys += w * analys.get(i);
                        sumPrest += w * prest.get(i);
                        sumTid += w * tid.get(i);
                        sumMot += w * motst.get(i);
                    }

                    double wAvgAnalys = sumAnalys / sumW;
                    double mean = analys.stream().mapToDouble(a -> a).average().orElse(0);
                    double var = analys.stream().mapToDouble(a -> (a - mean) * (a - mean)).average().orElse(0);
                    double std = Math.sqrt(var);

                    double score = wAvgAnalys - 0.5 * std;

                    int avgA = (int) Math.round(sumAnalys / sumW);
                    int avgP = (int) Math.round(sumPrest / sumW);
                    int avgT = (int) Math.round(sumTid / sumW);
                    int avgM = (int) Math.round(sumMot / sumW);

                    String startersStr = starters.stream().sorted().map(String::valueOf).distinct()
                            .collect(Collectors.joining(","));

                    return new WinnerSuggestion(
                            name, banKod, lap, startDate, formForReturn,
                            score, starters.size(), startersStr, avgA, avgP, avgT, avgM
                    );
                }).sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topN)
                .toList();

        System.out.println("pick_winner_across_starters: date=" + startDate + ", banKod=" + banKod + ", lap=" + lap + ", formIn=" + parsedForm + " -> using=" + formForReturn + " rows=" + rows.size());

        return ranked;
    }

    @Tool(
            name = "pick_winner_by_swedish_phrase",
            description = "Tolka en svensk fras med datum, bana, spelform och lopp (utan antal starter) och välj topp N över alla starter. Ex: 'Vem vinner på Solvalla 2025-09-03 med spelform vinnare i lopp 7?'"
    )
    public List<WinnerSuggestion> pickWinnerBySwedishPhrase(String phrase, Integer topN) {
        if (topN == null || topN <= 0) topN = 3;

        return pickWinnerAcrossStarters(phrase, phrase, phrase, phrase, topN);
    }

    @Tool(description = "Sök fram en häst och dess värden baserat på namnet på hästen")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        return horseResultRepo.findByNameOfHorseContainingIgnoreCase(nameFragment);
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

    @Tool(name = "find_tips_by_swedish_phrase",
            description = "Tolka svensk fras med datum, bana och lopp. Ex: 'Visa speltips för 2025 17 juli på Solvalla i lopp 5'. Stödjer även 'speltips 1' eller 'speltips 0'.")
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
        if (tipsValue == null && containsWordSpeltips(norm)) tipsValue = 1;

        if (tipsValue == null) {
            System.out.println("Inget explicit eller implicit speltipsvärde angivet.");
            return List.of();
        }

        List<HorseResult> rows = horseResultRepo
                .findByStartDateAndBanKodAndLapAndTips(date, banKod, lap, tipsValue);
        System.out.println("Tool find_tips_by_swedish_phrase hittade " + rows.size() + " rader med tips=" + tipsValue);
        return rows;
    }

    @Tool(name = "horses_with_speltips",
            description = "Lista hästar med speltips (tips=1) för datum, bana och lopp. Tar naturligt datum/bana/lopp.")
    public List<HorseResult> horsesWithSpeltips(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        Integer date = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapFlexible(lapOrPhrase);
        if (date == null || banKod == null || lap == null) return List.of();
        return horseResultRepo.findByStartDateAndBanKodAndLapAndTips(date, banKod, lap, 1);
    }

    @Tool(name = "results_by_date_track_lap_form_starter",
            description = "Hämta hästar för datum, bana, spelform, lopp och antal starter. Tar naturligt datum/bana/lopp (ex. '2025-09-02', 'Axevalla'/'S', 'lopp 3'), spelform (ex. 'vinnare', 'V75') och starter (ex. '5'). Sortera själv i klienten om du vill.")
    public List<HorseResult> resultsByDateTrackLapFormStarter(String dateOrPhrase,
                                                              String banKodOrTrack,
                                                              String lapOrPhrase,
                                                              String spelFormOrPhrase,
                                                              String starterOrPhrase) {
        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapFlexible(lapOrPhrase);
        String spelForm = parseSpelFormFlexible(spelFormOrPhrase);
        String starter = parseStarterFlexible(starterOrPhrase);

        if (startDate == null || banKod == null || lap == null || spelForm == null || starter == null) {
            return List.of();
        }
        return horseResultRepo.findByStartDateAndBanKodAndLapAndSpelFormIgnoreCaseAndStarter(
                startDate, banKod, lap, spelForm, starter);
    }

    @Tool(name = "results_by_swedish_phrase_with_form_and_starter",
            description = "Tolka en svensk fras med datum, bana, spelform, lopp och antal starter. Ex: 'Vem vinner på Axevalla 2025-09-02 med spelform vinnare i lopp 3 med 5 starter?'")
    public List<HorseResult> resultsBySwedishPhraseWithFormAndStarter(String phrase) {
        if (phrase == null || phrase.isBlank()) return List.of();
        String norm = normalize(phrase);

        Integer date = parseDateFromSwedish(norm);
        String banKod = toBanKod(norm);
        String lap = parseLap(norm);
        String spelForm = parseSpelFormFlexible(norm);
        String starter = parseStarterFlexible(norm);

        if (date == null || banKod == null || lap == null || spelForm == null || starter == null) {
            return List.of();
        }
        return horseResultRepo.findByStartDateAndBanKodAndLapAndSpelFormIgnoreCaseAndStarter(
                date, banKod, lap, spelForm, starter);
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
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    private static String parseAvdFlexible(String norm) {
        // "avd 3", "avdelning 2", "v75-1", "gs75 3", "v86:5"
        Matcher m1 = Pattern.compile("\\bavd(elning)?\\s*(\\d{1,2})\\b").matcher(norm);
        if (m1.find()) return m1.group(2);
        Matcher m2 = Pattern.compile("\\b(v75|v86|gs75|v64|v65|dd|ld)[-: ]?(\\d{1,2})\\b").matcher(norm);
        if (m2.find()) return m2.group(2);
        return null;
    }

    private static Integer parseMonthDayNoYear(String norm) {
        // fångar t.ex. 09-05 eller 9/5 -> år 2025 som default
        Matcher md = Pattern.compile("\\b(\\d{1,2})[-/ ](\\d{1,2})\\b").matcher(norm);
        if (md.find()) {
            int month = Integer.parseInt(md.group(1));
            int day = Integer.parseInt(md.group(2));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return 2025 * 10000 + month * 100 + day;
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

        Integer md = parseMonthDayNoYear(norm);
        if (md != null) return md;

        Matcher ymdDigits = Pattern.compile("(\\d{4})[-/ ]?(\\d{2})[-/ ]?(\\d{2})").matcher(norm);
        if (ymdDigits.find()) {
            int year = Integer.parseInt(ymdDigits.group(1));
            int month = Integer.parseInt(ymdDigits.group(2));
            int day = Integer.parseInt(ymdDigits.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return year * 10000 + month * 100 + day;
        }
        return null;
    }

    private static String parseLapOrAvdFlexible(String v) {
        if (v == null || v.isBlank()) return null;
        String norm = normalize(v);
        String avd = parseAvdFlexible(norm);
        if (avd != null) return avd;
        return parseLap(norm);
    }

    @Tool(name = "dates_all", description = "Lista tillgängliga datum (senaste först).")
    public List<Integer> datesAll() {
        return horseResultRepo.distinctDatesAll();
    }

    @Tool(name = "dates_by_track", description = "Lista tillgängliga datum för en bana/Bankod (senaste först).")
    public List<Integer> datesByTrack(String banKodOrTrack) {
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        if (banKod == null) return List.of();
        return horseResultRepo.distinctDatesByBanKod(banKod);
    }

    @Tool(name = "tracks_by_date", description = "Lista bankoder som finns på ett datum.")
    public List<String> tracksByDate(String dateOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        if (d == null) return List.of();
        return horseResultRepo.distinctBanKodByDate(d);
    }

    @Tool(name = "forms_by_date_track", description = "Lista spelformer för datum + bana.")
    public List<String> formsByDateTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return horseResultRepo.distinctSpelFormByDateAndBanKod(d, b);
    }

    @Tool(name = "lopp_by_date_track_form", description = "Lista lopp/avd för datum + bana (+ ev. spelform).")
    public List<String> loppByDateTrackForm(String dateOrPhrase, String banKodOrTrack, String spelFormOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String f = parseSpelFormFlexible(spelFormOrNull);
        if (d == null || b == null) return List.of();
        return horseResultRepo.distinctLapByDateBanKodAndForm(d, b, f);
    }

    @Tool(name = "starters_by_date_track_form_lopp", description = "Lista möjliga starter-värden för datum + bana + spelform + lopp/avd.")
    public List<String> startersByDateTrackFormLopp(String dateOrPhrase, String banKodOrTrack, String spelFormOrNull, String lapOrAvd) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String f = parseSpelFormFlexible(spelFormOrNull);
        String lap = parseLapOrAvdFlexible(lapOrAvd);
        if (d == null || b == null || lap == null) return List.of();
        return horseResultRepo.distinctStartersByDateBanKodFormLap(d, b, f, lap);
    }

    @Tool(name = "field_sorted", description = "Hämta hela fältet sorterat på Analys (desc) för datum + bana + lopp/avd (+ ev. spelform).")
    public List<HorseResult> fieldSorted(String dateOrPhrase, String banKodOrTrack, String lapOrAvd, String spelFormOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapOrAvdFlexible(lapOrAvd);
        String f = parseSpelFormFlexible(spelFormOrNull);
        if (d == null || b == null || lap == null) return List.of();

        List<HorseResult> rows = horseResultRepo.findField(d, b, lap, f);
        return rows.stream()
                .sorted(Comparator.comparingInt((HorseResult r) -> safeInt(r.getProcentAnalys())).reversed())
                .toList();
    }

    @Tool(name = "top_by_field", description = "Topp N i ett fält (datum + bana + lopp/avd + ev. spelform).")
    public List<HorseResult> topByField(String dateOrPhrase, String banKodOrTrack, String lapOrAvd, String spelFormOrNull, Integer topN) {
        if (topN == null || topN <= 0) topN = 3;
        return fieldSorted(dateOrPhrase, banKodOrTrack, lapOrAvd, spelFormOrNull).stream()
                .limit(topN)
                .toList();
    }

    @Tool(name = "top_by_field_with_starter", description = "Topp N för ett specifikt starter-värde (datum+bana+spelform+lopp/avd+starter).")
    public List<HorseResult> topByFieldWithStarter(String dateOrPhrase, String banKodOrTrack, String lapOrAvd, String spelForm, String starter, Integer topN) {
        if (topN == null || topN <= 0) topN = 3;
        List<HorseResult> rows = resultsByDateTrackLapFormStarter(dateOrPhrase, banKodOrTrack, lapOrAvd, spelForm, starter);
        return rows.stream()
                .sorted(Comparator.comparingInt((HorseResult r) -> safeInt(r.getProcentAnalys())).reversed())
                .limit(topN)
                .toList();
    }

    @Tool(name = "best_per_lopp", description = "Ge bästa häst (högst Analys) per lopp/avd för datum + bana (+ ev. spelform). Returnerar en rad per avdelning.")
    public List<PerLoppBest> bestPerLopp(String dateOrPhrase, String banKodOrTrack, String spelFormOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String f = parseSpelFormFlexible(spelFormOrNull);
        if (d == null || b == null) return List.of();

        List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(d, b, f);
        List<PerLoppBest> out = new ArrayList<>();
        for (String lap : laps) {
            List<HorseResult> field = horseResultRepo.findField(d, b, lap, f);
            field.sort(Comparator.comparingInt((HorseResult r) -> safeInt(r.getProcentAnalys())).reversed());
            if (!field.isEmpty()) {
                HorseResult top = field.get(0);
                out.add(new PerLoppBest(lap, top.getNameOfHorse(), safeInt(top.getProcentAnalys()), top.getNumberOfHorse()));
            }
        }
        return out;
    }

    public static class PerLoppBest {
        public String lap;
        public String name;
        public int analys;
        public Integer nr;
        public PerLoppBest(String lap, String name, int analys, Integer nr) { this.lap = lap; this.name = name; this.analys = analys; this.nr = nr; }
    }

    @Tool(name = "pick_winner_by_phrase_smart", description = "Som pick_winner_by_swedish_phrase men förstår även 'avd 3' och 'v75-1'.")
    public List<WinnerSuggestion> pickWinnerByPhraseSmart(String phrase, Integer topN) {
        if (topN == null || topN <= 0) topN = 3;
        String norm = normalize(phrase);
        String lap = parseAvdFlexible(norm);
        String effective = phrase;
        if (lap != null && !norm.contains("lopp")) {
            effective = phrase + " lopp " + lap;
        }
        return pickWinnerBySwedishPhrase(effective, topN);
    }


    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean containsWordSpeltips(String norm) {
        return norm.contains("speltips");
    }

    private static Integer parseExplicitTipsValue(String norm) {
        Matcher m = Pattern.compile("(speltips|tips)\\s*(=|ar|är|:)?\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(norm);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(3));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String parseLap(String norm) {
        Matcher m1 = Pattern.compile("\\blopp\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(norm);
        if (m1.find()) return m1.group(1);

        Matcher m2 = Pattern.compile("(\\d{1,2})(?!\\d)").matcher(norm);
        String last = null;
        while (m2.find()) last = m2.group(1);
        return last;
    }

    private static Integer parseDateFromSwedish(String norm) {
        if (norm == null) return null;
        Matcher ydm = Pattern.compile("(\\d{4})\\D{0,5}(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)").matcher(norm);
        if (ydm.find()) {
            int year = Integer.parseInt(ydm.group(1));
            int day = Integer.parseInt(ydm.group(2));
            int month = svMonth.getOrDefault(ydm.group(3), 0);
            if (month >= 1 && day >= 1 && day <= 31) return year * 10000 + month * 100 + day;
        }
        Matcher dmy = Pattern.compile("(\\d{1,2})\\D{0,5}(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)\\D{0,5}(\\d{4})").matcher(norm);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = svMonth.getOrDefault(dmy.group(2), 0);
            int year = Integer.parseInt(dmy.group(3));
            if (month >= 1 && day >= 1 && day <= 31) return year * 10000 + month * 100 + day;
        }
        Matcher ymdDigits = Pattern.compile("(\\d{4})[-/ ]?(\\d{2})[-/ ]?(\\d{2})").matcher(norm);
        if (ymdDigits.find()) {
            int year = Integer.parseInt(ymdDigits.group(1));
            int month = Integer.parseInt(ymdDigits.group(2));
            int day = Integer.parseInt(ymdDigits.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return year * 10000 + month * 100 + day;
        }
        return null;
    }

    private static String resolveBanKodFlexible(String banKodOrTrack) {
        if (banKodOrTrack == null || banKodOrTrack.isBlank()) return null;
        String norm = normalize(banKodOrTrack);
        return toBanKod(norm);
    }

    private static String toBanKod(String norm) {
        if (norm == null) return null;
        for (String code : tackToBanKod.keySet()) {
            if (norm.matches(".*\\b" + normalize(code) + "\\b.*")) return code;
        }
        for (Map.Entry<String, String> e : tackToBanKod.entrySet()) {
            if (norm.contains(normalize(e.getValue()))) return e.getKey();
        }
        return null;
    }

    private static String parseSpelFormFlexible(String v) {
        if (v == null || v.isBlank()) return "vinnare";
        String n = normalize(v);

        if (n.contains("vem vinner") || n.contains(" vinner") || n.contains(" vinn ")) return "vinnare";

        Matcher m = Pattern.compile("\\bspelform\\s*([a-z0-9]+)").matcher(n);
        if (m.find()) return m.group(1);

        String[] known = {"vinnare", "plats", "v75", "v86", "gs75", "v64", "v65", "dd", "ld"};
        for (String k : known) {
            if (n.contains(k)) return k;
        }

        String sanitized = n.replaceAll("[^a-z0-9]", "");
        return sanitized.isBlank() ? "vinnare" : sanitized;
    }

    private static boolean isAggregatorSpelForm(String s) {
        if (s == null) return false;
        String n = s.toLowerCase(Locale.ROOT).trim();
        return n.equals("trio") || n.equals("tvilling") || n.equals("komb") || n.equals("trippel") || n.equals("triple");
    }

    private static String parseStarterFlexible(String v) {
        if (v == null || v.isBlank()) return null;
        String n = normalize(v);
        Matcher m = Pattern.compile("(\\d+)\\s*starter").matcher(n);
        if (m.find()) return m.group(1);
        Matcher onlyNum = Pattern.compile("^(\\d+)$").matcher(n);
        if (onlyNum.find()) return onlyNum.group(1);
        return null;
    }

    private static String parseLapFlexible(String lapOrPhrase) {
        if (lapOrPhrase == null || lapOrPhrase.isBlank()) return null;
        return parseLap(normalize(lapOrPhrase));
    }

    private int parse(String val) {
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return -1;
        }
    }

    public static class WinnerSuggestion {
        public String name;
        public String banKod;
        public String lap;
        public Integer startDate;
        public String spelForm;
        public double score;
        public int variants;
        public String starters;
        public int avgAnalys;
        public int avgPrestation;
        public int avgTid;
        public int avgMotstand;

        public WinnerSuggestion() {
        }

        public WinnerSuggestion(String name, String banKod, String lap, Integer startDate, String spelForm,
                                double score, int variants, String starters,
                                int avgAnalys, int avgPrestation, int avgTid, int avgMotstand) {
            this.name = name;
            this.banKod = banKod;
            this.lap = lap;
            this.startDate = startDate;
            this.spelForm = spelForm;
            this.score = score;
            this.variants = variants;
            this.starters = starters;
            this.avgAnalys = avgAnalys;
            this.avgPrestation = avgPrestation;
            this.avgTid = avgTid;
            this.avgMotstand = avgMotstand;
        }
    }
}
