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

    // ------------------------------------------------------------
    // NYTT: Snapshot för ALLA banor på ett datum (spelform + lopp + starter 0-8)
    // ------------------------------------------------------------

    public static class DaySnapshot { //Changed!
        public Integer startDate; //Changed!
        public String requestedSpelForm; //Changed!
        public List<TrackSnapshot> tracks; //Changed!
        public DaySnapshot(Integer startDate, String requestedSpelForm, List<TrackSnapshot> tracks) { //Changed!
            this.startDate = startDate; //Changed!
            this.requestedSpelForm = requestedSpelForm; //Changed!
            this.tracks = tracks; //Changed!
        } //Changed!
    } //Changed!

    public static class TrackSnapshot { //Changed!
        public String banKod; //Changed!
        public String spelFormUsed; //Changed!
        public List<LapSnapshot> laps; //Changed!
        public TrackSnapshot(String banKod, String spelFormUsed, List<LapSnapshot> laps) { //Changed!
            this.banKod = banKod; //Changed!
            this.spelFormUsed = spelFormUsed; //Changed!
            this.laps = laps; //Changed!
        } //Changed!
    } //Changed!

    public static class LapSnapshot { //Changed!
        public String lap; //Changed!
        public List<Integer> starters; //Changed!
        public LapSnapshot(String lap, List<Integer> starters) { //Changed!
            this.lap = lap; //Changed!
            this.starters = starters; //Changed!
        } //Changed!
    } //Changed!

    @Tool( //Changed!
            name = "snapshot_by_date_form_all_tracks", //Changed!
            description = "Returnerar alla banor för ett datum, samt alla lopp/avd och vilka starter (0-8) som finns per lopp, filtrerat på spelform (med fallback till vinnare/utan spelform)." //Changed!
    ) //Changed!
    public DaySnapshot snapshotByDateFormAllTracks(String dateOrPhrase, String spelFormOrPhrase) { //Changed!
        Integer startDate = parseDateFlexible(dateOrPhrase); //Changed!
        if (startDate == null) return new DaySnapshot(null, null, List.of()); //Changed!

        String parsedForm = parseSpelFormFlexible(spelFormOrPhrase); //Changed!
        if (isAggregatorSpelForm(parsedForm)) parsedForm = "vinnare"; //Changed!
        String requestedForm = (parsedForm == null ? "vinnare" : parsedForm); //Changed!

        List<String> tracks = horseResultRepo.distinctBanKodByDate(startDate); //Changed!
        List<TrackSnapshot> out = new ArrayList<>(); //Changed!

        for (String banKod : tracks) { //Changed!
            String formUsed = requestedForm; //Changed!
            List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, formUsed); //Changed!

            if (laps.isEmpty() && !"vinnare".equalsIgnoreCase(formUsed)) { //Changed!
                formUsed = "vinnare"; //Changed!
                laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, formUsed); //Changed!
            } //Changed!
            if (laps.isEmpty()) { //Changed!
                formUsed = null; //Changed!
                laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, null); //Changed!
            } //Changed!

            List<LapSnapshot> lapSnaps = new ArrayList<>(); //Changed!
            for (String lap : laps.stream().sorted(Comparator.comparingInt(TravTools::lapKey)).toList()) { //Changed!
                List<String> startersRaw = horseResultRepo.distinctStartersByDateBanKodFormLap(startDate, banKod, formUsed, lap); //Changed!
                List<Integer> starters = (startersRaw == null ? List.<Integer>of() : startersRaw.stream() //Changed!
                        .map(TravTools::safeInt) //Changed!
                        .filter(x -> x >= 0 && x <= 8) //Changed!
                        .distinct() //Changed!
                        .sorted() //Changed!
                        .toList()); //Changed!

                lapSnaps.add(new LapSnapshot(lap, starters)); //Changed!
            } //Changed!

            out.add(new TrackSnapshot(banKod, (formUsed == null ? "ALL" : formUsed), lapSnaps)); //Changed!
        } //Changed!

        return new DaySnapshot(startDate, requestedForm, out); //Changed!
    } //Changed!

    // ------------------------------------------------------------
    // Starter=0 (aggregerat) prioritet
    // ------------------------------------------------------------

    private static boolean isStarterZero(HorseResult r) { //Changed!
        return safeInt(r.getStarter()) == 0; //Changed!
    }

    private static List<HorseResult> preferStarterZero(List<HorseResult> rows) { //Changed!
        if (rows == null || rows.isEmpty()) return rows; //Changed!
        List<HorseResult> zero = rows.stream() //Changed!
                .filter(TravTools::isStarterZero) //Changed!
                .toList(); //Changed!
        return zero.isEmpty() ? rows : zero; //Changed!
    }

    private static int lapKey(String lap) { //Changed!
        try { return Integer.parseInt(lap); } catch (Exception e) { return Integer.MAX_VALUE; } //Changed!
    }

    private static double bonusFromPerspectives( //Changed!
                                                 int avgPrestation, int avgForm, int avgFart, int avgMotstand, //Changed!
                                                 int avgKlass, int avgSkrik, int avgPlacering //Changed!
    ) { //Changed!
        // Håll bonusen liten så Analys fortfarande styr. //Changed!
        double raw = 0.06 * avgPrestation
                + 0.05 * avgForm
                + 0.04 * avgFart
                + 0.03 * avgMotstand
                + 0.02 * avgKlass
                + 0.01 * avgSkrik
                + 0.01 * avgPlacering; //Changed!
        return raw * 0.20; //Changed! // ~0–4 poäng typ
    } //Changed!


    // ------------------------------------------------------------
    // NYTT: Placering tolkas från hästnamn "PedroHorse (4)"
    // ------------------------------------------------------------

    private static Integer placementTop6FromName(String horseName) { //Changed!
        if (horseName == null) return null; //Changed!
        Matcher m = Pattern.compile("\\((\\d)\\)\\s*$").matcher(horseName.trim()); //Changed!
        if (!m.find()) return null; //Changed!
        int p = Integer.parseInt(m.group(1)); //Changed!
        return (p >= 1 && p <= 6) ? p : null; //Changed!
    } //Changed!

    private static String stripPlacementFromName(String horseName) { //Changed!
        if (horseName == null) return null; //Changed!
        return horseName.replaceAll("\\s*\\(\\d\\)\\s*$", "").trim(); //Changed!
    } //Changed!

    // ------------------------------------------------------------
    // Basic tools
    // ------------------------------------------------------------

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

        rows = preferStarterZero(rows); //Changed!

        if (rows.isEmpty() || aggregator) {
            effectiveForm = "vinnare"; //Changed!
            rows = horseResultRepo.findByStartDateAndBanKodAndLapAndSpelFormIgnoreCase(startDate, banKod, lap, "vinnare");
            rows = preferStarterZero(rows); //Changed!
        }

        if (rows.isEmpty()) {
            rows = horseResultRepo.findByStartDateAndBanKodAndLap(startDate, banKod, lap);
            rows = preferStarterZero(rows); //Changed!
            effectiveForm = "vinnare"; //Changed!
        }

        if (rows.isEmpty()) return List.of();

        final String formForReturn = effectiveForm;

        Map<String, List<HorseResult>> byHorse = rows.stream() //Changed!
                .collect(Collectors.groupingBy(r -> stripPlacementFromName(r.getNameOfHorse()))); //Changed!

        List<WinnerSuggestion> ranked = byHorse.entrySet().stream().map(e -> {
                    String baseName = e.getKey(); //Changed!
                    List<HorseResult> list = e.getValue();

                    Integer placement = list.stream() //Changed!
                            .map(r -> placementTop6FromName(r.getNameOfHorse())) //Changed!
                            .filter(Objects::nonNull) //Changed!
                            .min(Integer::compareTo) //Changed!
                            .orElse(null); //Changed!

                    String displayName = (placement == null) ? baseName : (baseName + " (" + placement + ")"); //Changed!

                    List<Integer> starters = new ArrayList<>();
                    List<Integer> analys = new ArrayList<>();
                    List<Integer> prest = new ArrayList<>();
                    List<Integer> tid = new ArrayList<>();
                    List<Integer> motst = new ArrayList<>();

                    List<Integer> klass = new ArrayList<>(); //Changed!
                    List<Integer> skrik = new ArrayList<>(); //Changed!
                    List<Integer> plac = new ArrayList<>(); //Changed!
                    List<Integer> formv = new ArrayList<>(); //Changed!

                    for (HorseResult r : list) {
                        int s = safeInt(r.getStarter());
                        starters.add(s);

                        analys.add(safeInt(r.getProcentAnalys()));
                        prest.add(safeInt(r.getProcentPrestation()));
                        tid.add(safeInt(r.getProcentFart()));
                        motst.add(safeInt(r.getProcentMotstand()));

                        klass.add(safeInt(r.getKlassProcent())); //Changed!
                        skrik.add(safeInt(r.getProcentSkrik())); //Changed!
                        plac.add(safeInt(r.getProcentPlacering())); //Changed!
                        formv.add(safeInt(r.getProcentForm())); //Changed!
                    }

                    double sumW = 0;
                    double sumAnalys = 0, sumPrest = 0, sumTid = 0, sumMot = 0;
                    double sumKlass = 0, sumSkrik = 0, sumPlac = 0, sumForm = 0; //Changed!

                    for (int i = 0; i < starters.size(); i++) {
                        double w = Math.sqrt(Math.max(1, starters.get(i)));
                        sumW += w;

                        sumAnalys += w * analys.get(i);
                        sumPrest += w * prest.get(i);
                        sumTid += w * tid.get(i);
                        sumMot += w * motst.get(i);

                        sumKlass += w * klass.get(i); //Changed!
                        sumSkrik += w * skrik.get(i); //Changed!
                        sumPlac  += w * plac.get(i);  //Changed!
                        sumForm  += w * formv.get(i); //Changed!
                    }

                    double wAvgAnalys = sumAnalys / sumW;

                    double mean = analys.stream().mapToDouble(a -> a).average().orElse(0);
                    double var = analys.stream().mapToDouble(a -> (a - mean) * (a - mean)).average().orElse(0);
                    double std = Math.sqrt(var);

                    int avgA = (int) Math.round(sumAnalys / sumW);
                    int avgP = (int) Math.round(sumPrest / sumW);
                    int avgT = (int) Math.round(sumTid / sumW);
                    int avgM = (int) Math.round(sumMot / sumW);

                    int avgK = (int) Math.round(sumKlass / sumW); //Changed!
                    int avgS = (int) Math.round(sumSkrik / sumW); //Changed!
                    int avgPl = (int) Math.round(sumPlac / sumW); //Changed!
                    int avgF = (int) Math.round(sumForm / sumW); //Changed!

                    double baseScore = wAvgAnalys - 0.5 * std; //Changed!
                    double bonus = bonusFromPerspectives(avgP, avgF, avgT, avgM, avgK, avgS, avgPl); //Changed!
                    double score = baseScore + bonus; //Changed!

                    String startersStr = starters.stream().sorted().map(String::valueOf).distinct()
                            .collect(Collectors.joining(","));

                    return new WinnerSuggestion(
                            displayName, banKod, lap, startDate, formForReturn, //Changed!
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
        Matcher m1 = Pattern.compile("\\bavd(elning)?\\s*(\\d{1,2})\\b").matcher(norm);
        if (m1.find()) return m1.group(2);
        Matcher m2 = Pattern.compile("\\b(v75|v86|gs75|v64|v65|dd|ld)[-: ]?(\\d{1,2})\\b").matcher(norm);
        if (m2.find()) return m2.group(2);
        return null;
    }

    private static Integer parseMonthDayNoYear(String norm) {
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
        rows = preferStarterZero(rows); //Changed!
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
            field = preferStarterZero(field); //Changed!
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

    // ------------------------------------------------------------
    // NYTT: topp N per avd/lopp för en hel dag
    // ------------------------------------------------------------

    public static class LoppTopN { //Changed!
        public String lap; //Changed!
        public List<WinnerSuggestion> top; //Changed!
        public LoppTopN(String lap, List<WinnerSuggestion> top) { this.lap = lap; this.top = top; } //Changed!
    }

    @Tool(name = "top_by_day_track_form",
            description = "Topp N per lopp/avd för ett datum + bana + spelform. Prioriterar starter=0 om finns i avdelningen, annars väger den ihop starter 1-8.")
    public List<LoppTopN> topByDayTrackForm(String dateOrPhrase, String banKodOrTrack, String spelFormOrPhrase, Integer topN) { //Changed!

        Integer startDate = parseDateFlexible(dateOrPhrase); //Changed!
        String banKod = resolveBanKodFlexible(banKodOrTrack); //Changed!
        String form = parseSpelFormFlexible(spelFormOrPhrase); //Changed!
        if (topN == null || topN <= 0) topN = 3; //Changed!
        if (startDate == null || banKod == null) return List.of(); //Changed!

        boolean aggregator = isAggregatorSpelForm(form); //Changed!
        String effectiveForm = (form == null ? "vinnare" : form); //Changed!

        List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, effectiveForm); //Changed!

        if (laps.isEmpty() || aggregator) { //Changed!
            effectiveForm = "vinnare"; //Changed!
            laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, "vinnare"); //Changed!
        }
        if (laps.isEmpty()) { //Changed!
            laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, null); //Changed!
        }

        final String formForCalls = effectiveForm; //Changed!

        Integer finalTopN = topN;
        return laps.stream()
                .sorted(Comparator.comparingInt(TravTools::lapKey)) //Changed!
                .map(lap -> new LoppTopN( //Changed!
                        lap, //Changed!
                        pickWinnerAcrossStarters(String.valueOf(startDate), banKod, lap, formForCalls, finalTopN) //Changed!
                ))
                .toList(); //Changed!
    }

    @Tool(name = "top_by_day_phrase",
            description = "Som top_by_day_track_form men tar en svensk fras. Ex: 'Visa topp 3 i alla avdelningar på Solvalla 2025-12-03 spelform vinnare'")
    public List<LoppTopN> topByDayPhrase(String phrase, Integer topN) { //Changed!
        return topByDayTrackForm(phrase, phrase, phrase, topN); //Changed!
    }

    // ------------------------------------------------------------
    // NYTT: Globala spikar över ALLA banor för ett datum
    // ------------------------------------------------------------

    public static class GlobalSpikSuggestion { //Changed!
        public String name; //Changed!
        public String banKod; //Changed!
        public String lap; //Changed!
        public Integer startDate; //Changed!
        public String spelForm; //Changed!
        public double spikScore; //Changed!
        public double winnerScore; //Changed!
        public double edgeVsSecond; //Changed!
        public String secondName; //Changed!
        public Double secondScore; //Changed!
        public String starters; //Changed!

        public GlobalSpikSuggestion() { } //Changed!

        public GlobalSpikSuggestion(String name, String banKod, String lap, Integer startDate, String spelForm, //Changed!
                                    double spikScore, double winnerScore, double edgeVsSecond, //Changed!
                                    String secondName, Double secondScore, String starters) { //Changed!
            this.name = name; //Changed!
            this.banKod = banKod; //Changed!
            this.lap = lap; //Changed!
            this.startDate = startDate; //Changed!
            this.spelForm = spelForm; //Changed!
            this.spikScore = spikScore; //Changed!
            this.winnerScore = winnerScore; //Changed!
            this.edgeVsSecond = edgeVsSecond; //Changed!
            this.secondName = secondName; //Changed!
            this.secondScore = secondScore; //Changed!
            this.starters = starters; //Changed!
        } //Changed!
    } //Changed!

    @Tool( //Changed!
            name = "pick_spikar_all_tracks_by_date_form", //Changed!
            description = "Välj N spikar globalt över alla banor för ett datum + spelform. Tar top2 per lopp och rankar på spikScore = winnerScore + (winnerScore - secondScore)." //Changed!
    ) //Changed!
    public List<GlobalSpikSuggestion> pickSpikarAllTracksByDateForm(String dateOrPhrase, String spelFormOrPhrase, Integer count) { //Changed!
        if (count == null || count <= 0) count = 5; //Changed!

        DaySnapshot snap = snapshotByDateFormAllTracks(dateOrPhrase, spelFormOrPhrase); //Changed!
        if (snap == null || snap.startDate == null || snap.tracks == null || snap.tracks.isEmpty()) return List.of(); //Changed!

        List<GlobalSpikSuggestion> candidates = new ArrayList<>(); //Changed!

        for (TrackSnapshot t : snap.tracks) { //Changed!
            if (t == null || t.laps == null) continue; //Changed!
            for (LapSnapshot l : t.laps) { //Changed!
                if (l == null || l.lap == null) continue; //Changed!

                List<WinnerSuggestion> top2 = pickWinnerAcrossStarters( //Changed!
                        String.valueOf(snap.startDate), //Changed!
                        t.banKod, //Changed!
                        l.lap, //Changed!
                        spelFormOrPhrase, //Changed!
                        2 //Changed!
                ); //Changed!

                if (top2 == null || top2.isEmpty()) continue; //Changed!

                WinnerSuggestion first = top2.get(0); //Changed!
                WinnerSuggestion second = (top2.size() > 1) ? top2.get(1) : null; //Changed!

                double edge = (second == null) ? 0.0 : (first.score - second.score); //Changed!
                double spikScore = first.score + edge; //Changed!

                candidates.add(new GlobalSpikSuggestion( //Changed!
                        first.name, //Changed!
                        first.banKod, //Changed!
                        first.lap, //Changed!
                        first.startDate, //Changed!
                        first.spelForm, //Changed!
                        spikScore, //Changed!
                        first.score, //Changed!
                        edge, //Changed!
                        (second == null ? null : second.name), //Changed!
                        (second == null ? null : second.score), //Changed!
                        first.starters //Changed!
                ));
            } //Changed!
        } //Changed!

        return candidates.stream() //Changed!
                .sorted((a, b) -> Double.compare(b.spikScore, a.spikScore)) //Changed!
                .limit(count) //Changed!
                .toList(); //Changed!
    } //Changed!

    @Tool(name = "pick_spikar_across_laps",
            description = "Välj N spikar (vinnare) från olika lopp/avd för datum+bana+spelform. Tar bästa top1 per avd och väljer sedan de N starkaste.")
    public List<WinnerSuggestion> pickSpikarAcrossLaps(String dateOrPhrase, String banKodOrTrack, String spelFormOrPhrase, Integer count) { //Changed!

        if (count == null || count <= 0) count = 2; //Changed!

        List<LoppTopN> perLap = topByDayTrackForm(dateOrPhrase, banKodOrTrack, spelFormOrPhrase, 2); //Changed! (1 -> 2)

        return perLap.stream()
                .map(x -> { //Changed!
                    if (x.top == null || x.top.isEmpty()) return null; //Changed!
                    WinnerSuggestion first = x.top.get(0); //Changed!
                    WinnerSuggestion second = (x.top.size() > 1) ? x.top.get(1) : null; //Changed!
                    double edge = (second == null) ? 0.0 : (first.score - second.score); //Changed!
                    double spikScore = first.score + edge; //Changed!
                    return new WinnerSuggestion( //Changed!
                            first.name, first.banKod, first.lap, first.startDate, first.spelForm, //Changed!
                            spikScore, first.variants, first.starters, //Changed!
                            first.avgAnalys, first.avgPrestation, first.avgTid, first.avgMotstand //Changed!
                    ); //Changed!
                })
                .filter(Objects::nonNull) //Changed!
                .sorted((a, b) -> Double.compare(b.score, a.score)) //Changed!
                .limit(count) //Changed!
                .toList(); //Changed!
    }

    @Tool(name = "pick_spikar_by_phrase",
            description = "Tolka svensk fras och välj 2 spikar från olika avdelningar. Ex: 'Ge mig 2 spikar på Solvalla 2025-12-03 spelform vinnare'")
    public List<WinnerSuggestion> pickSpikarByPhrase(String phrase, Integer count) { //Changed!
        return pickSpikarAcrossLaps(phrase, phrase, phrase, count); //Changed!
    }

    // ------------------------------------------------------------
    // NYTT: Prediktion med historik över ALLA banor före datumet
    // ------------------------------------------------------------

    public static class HistoryStats { //Changed!
        public String horse; //Changed!
        public int races; //Changed!
        public int top6; //Changed!
        public int wins; //Changed!
        public double winRate; //Changed!
        public double top6Rate; //Changed!
        public Double avgPlacementTop6; //Changed!
        public Integer lastDate; //Changed!

        public HistoryStats() {} //Changed!

        public HistoryStats(String horse, int races, int top6, int wins, double winRate, double top6Rate, Double avgPlacementTop6, Integer lastDate) { //Changed!
            this.horse = horse; //Changed!
            this.races = races; //Changed!
            this.top6 = top6; //Changed!
            this.wins = wins; //Changed!
            this.winRate = winRate; //Changed!
            this.top6Rate = top6Rate; //Changed!
            this.avgPlacementTop6 = avgPlacementTop6; //Changed!
            this.lastDate = lastDate; //Changed!
        } //Changed!
    } //Changed!

    public static class WinnerWithHistory { //Changed!
        public WinnerSuggestion pick; //Changed!
        public double combinedScore; //Changed!
        public double historyBoost; //Changed!
        public HistoryStats history; //Changed!

        public WinnerWithHistory() {} //Changed!

        public WinnerWithHistory(WinnerSuggestion pick, double combinedScore, double historyBoost, HistoryStats history) { //Changed!
            this.pick = pick; //Changed!
            this.combinedScore = combinedScore; //Changed!
            this.historyBoost = historyBoost; //Changed!
            this.history = history; //Changed!
        } //Changed!
    } //Changed!

    public static class LapPredictionWithHistory { //Changed!
        public String lap; //Changed!
        public List<WinnerWithHistory> top; //Changed!
        public LapPredictionWithHistory() {} //Changed!
        public LapPredictionWithHistory(String lap, List<WinnerWithHistory> top) { //Changed!
            this.lap = lap; //Changed!
            this.top = top; //Changed!
        } //Changed!
    } //Changed!

    public static class TrackPredictionWithHistory { //Changed!
        public String banKod; //Changed!
        public String spelFormUsed; //Changed!
        public List<LapPredictionWithHistory> laps; //Changed!
        public TrackPredictionWithHistory() {} //Changed!
        public TrackPredictionWithHistory(String banKod, String spelFormUsed, List<LapPredictionWithHistory> laps) { //Changed!
            this.banKod = banKod; //Changed!
            this.spelFormUsed = spelFormUsed; //Changed!
            this.laps = laps; //Changed!
        } //Changed!
    } //Changed!

    public static class DayPredictionWithHistory { //Changed!
        public Integer startDate; //Changed!
        public String requestedSpelForm; //Changed!
        public List<TrackPredictionWithHistory> tracks; //Changed!
        public DayPredictionWithHistory() {} //Changed!
        public DayPredictionWithHistory(Integer startDate, String requestedSpelForm, List<TrackPredictionWithHistory> tracks) { //Changed!
            this.startDate = startDate; //Changed!
            this.requestedSpelForm = requestedSpelForm; //Changed!
            this.tracks = tracks; //Changed!
        } //Changed!
    } //Changed!

    @Tool( //Changed!
            name = "predict_day_all_tracks_using_history", //Changed!
            description = "För ett givet datum+spelform: listar alla banor+lopp som finns (snapshot), plockar toppkandidater per lopp och justerar rankingen med historik före datumet över ALLA banor. Historik tolkar topp6 via '(1)-(6)' i hästnamn." //Changed!
    ) //Changed!
    public DayPredictionWithHistory predictDayAllTracksUsingHistory(String dateOrPhrase, String spelFormOrPhrase, Integer topN, Integer historyLimitPerHorse) { //Changed!
        Integer targetDate = parseDateFlexible(dateOrPhrase); //Changed!
        if (targetDate == null) return new DayPredictionWithHistory(null, null, List.of()); //Changed!

        if (topN == null || topN <= 0) topN = 3; //Changed!
        if (historyLimitPerHorse == null || historyLimitPerHorse <= 0) historyLimitPerHorse = 200; //Changed!

        String form = parseSpelFormFlexible(spelFormOrPhrase); //Changed!
        if (isAggregatorSpelForm(form)) form = "vinnare"; //Changed!
        String requestedForm = (form == null ? "vinnare" : form); //Changed!

        DaySnapshot snap = snapshotByDateFormAllTracks(String.valueOf(targetDate), requestedForm); //Changed!
        if (snap == null || snap.tracks == null || snap.tracks.isEmpty()) { //Changed!
            return new DayPredictionWithHistory(targetDate, requestedForm, List.of()); //Changed!
        } //Changed!

        Map<String, HistoryStats> historyCache = new HashMap<>(); //Changed!
        List<TrackPredictionWithHistory> outTracks = new ArrayList<>(); //Changed!

        for (TrackSnapshot t : snap.tracks) { //Changed!
            if (t == null || t.laps == null) continue; //Changed!

            List<LapPredictionWithHistory> outLaps = new ArrayList<>(); //Changed!

            for (LapSnapshot l : t.laps) { //Changed!
                if (l == null || l.lap == null) continue; //Changed!

                int candidateN = Math.max(topN * 2, 6); //Changed!

                List<WinnerSuggestion> candidates = pickWinnerAcrossStarters( //Changed!
                        String.valueOf(targetDate), //Changed!
                        t.banKod, //Changed!
                        l.lap, //Changed!
                        requestedForm, //Changed!
                        candidateN //Changed!
                ); //Changed!

                if (candidates == null || candidates.isEmpty()) { //Changed!
                    outLaps.add(new LapPredictionWithHistory(l.lap, List.of())); //Changed!
                    continue; //Changed!
                } //Changed!

                List<WinnerWithHistory> ranked = new ArrayList<>(); //Changed!

                for (WinnerSuggestion w : candidates) { //Changed!
                    if (w == null || w.name == null) continue; //Changed!

                    String baseName = stripPlacementFromName(w.name); //Changed!
                    if (baseName == null || baseName.isBlank()) continue; //Changed!

                    Integer finalHistoryLimitPerHorse = historyLimitPerHorse;
                    HistoryStats hs = historyCache.computeIfAbsent( //Changed!
                            normalize(baseName), //Changed!
                            k -> buildHistoryStatsForHorse(baseName, targetDate, requestedForm, finalHistoryLimitPerHorse) //Changed!
                    ); //Changed!

                    double boost = scoreBoostFromHistory(hs); //Changed!
                    double combined = w.score + boost; //Changed!

                    ranked.add(new WinnerWithHistory(w, combined, boost, hs)); //Changed!
                } //Changed!

                List<WinnerWithHistory> top = ranked.stream() //Changed!
                        .sorted((a, b) -> Double.compare(b.combinedScore, a.combinedScore)) //Changed!
                        .limit(topN) //Changed!
                        .toList(); //Changed!

                outLaps.add(new LapPredictionWithHistory(l.lap, top)); //Changed!
            } //Changed!

            outTracks.add(new TrackPredictionWithHistory(t.banKod, t.spelFormUsed, outLaps)); //Changed!
        } //Changed!

        return new DayPredictionWithHistory(targetDate, requestedForm, outTracks); //Changed!
    } //Changed!

    private HistoryStats buildHistoryStatsForHorse(String baseName, Integer beforeDate, String spelForm, int limit) { //Changed!
        if (baseName == null || baseName.isBlank() || beforeDate == null) { //Changed!
            return new HistoryStats(baseName, 0, 0, 0, 0, 0, null, null); //Changed!
        } //Changed!

        List<HorseResult> rows = horseResultRepo.findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(baseName); //Changed!
        if (rows == null || rows.isEmpty()) { //Changed!
            return new HistoryStats(baseName, 0, 0, 0, 0, 0, null, null); //Changed!
        } //Changed!

        int races = 0, top6 = 0, wins = 0; //Changed!
        double sumPlacement = 0; //Changed!
        int placementCount = 0; //Changed!
        Integer lastDate = null; //Changed!

        Set<String> seenRace = new HashSet<>(); //Changed!

        for (HorseResult r : rows) { //Changed!
            if (r == null) continue; //Changed!

            Integer d = r.getStartDate(); //Changed!
            if (d == null || d >= beforeDate) continue; //Changed!

            String rowBase = stripPlacementFromName(r.getNameOfHorse()); //Changed!
            if (rowBase == null || !rowBase.equalsIgnoreCase(baseName)) continue; //Changed!

            if (spelForm != null && r.getSpelForm() != null) { //Changed!
                if (!normalize(r.getSpelForm()).equals(normalize(spelForm))) continue; //Changed!
            } //Changed!

            String raceKey = d + "|" + r.getBanKod() + "|" + r.getLap(); //Changed!
            if (!seenRace.add(raceKey)) continue; //Changed!

            races++; //Changed!
            if (lastDate == null) lastDate = d; //Changed!

            Integer p = placementTop6FromName(r.getNameOfHorse()); //Changed!
            if (p != null) { //Changed!
                top6++; //Changed!
                if (p == 1) wins++; //Changed!
                sumPlacement += p; //Changed!
                placementCount++; //Changed!
            } //Changed!

            if (races >= limit) break; //Changed!
        } //Changed!

        double winRate = (races == 0) ? 0.0 : (wins / (double) races); //Changed!
        double top6Rate = (races == 0) ? 0.0 : (top6 / (double) races); //Changed!
        Double avgP = (placementCount == 0) ? null : (sumPlacement / placementCount); //Changed!

        return new HistoryStats(baseName, races, top6, wins, winRate, top6Rate, avgP, lastDate); //Changed!
    } //Changed!

    private static double scoreBoostFromHistory(HistoryStats hs) { //Changed!
        if (hs == null || hs.races <= 0) return 0.0; //Changed!

        double boost = 15.0 * hs.winRate + 5.0 * hs.top6Rate; //Changed!

        if (hs.avgPlacementTop6 != null) { //Changed!
            boost += Math.max(0.0, (7.0 - hs.avgPlacementTop6) * 0.5); //Changed!
        } //Changed!

        return boost; //Changed!
    } //Changed!

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static int safeInt(String s) { //Changed!
        if (s == null) return 0; //Changed!
        try { //Changed!
            String cleaned = s.replaceAll("[^0-9-]", ""); //Changed!
            if (cleaned.isBlank() || "-".equals(cleaned)) return 0; //Changed!
            return Integer.parseInt(cleaned); //Changed!
        } catch (Exception e) { //Changed!
            return 0; //Changed!
        } //Changed!
    } //Changed!


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

    // ------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------

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
    public static class GlobalSpikSuggestionWithHistory { //Changed!
        public String name; //Changed!
        public String banKod; //Changed!
        public String lap; //Changed!
        public Integer startDate; //Changed!
        public String spelForm; //Changed!
        public double spikScore; //Changed!
        public double combinedWinnerScore; //Changed!
        public double edgeVsSecond; //Changed!
        public String secondName; //Changed!
        public Double combinedSecondScore; //Changed!
        public double historyBoost; //Changed!
        public String starters; //Changed!

        public GlobalSpikSuggestionWithHistory() {} //Changed!

        public GlobalSpikSuggestionWithHistory( //Changed!
                                                String name, String banKod, String lap, Integer startDate, String spelForm, //Changed!
                                                double spikScore, double combinedWinnerScore, double edgeVsSecond, //Changed!
                                                String secondName, Double combinedSecondScore, double historyBoost, String starters //Changed!
        ) { //Changed!
            this.name = name; //Changed!
            this.banKod = banKod; //Changed!
            this.lap = lap; //Changed!
            this.startDate = startDate; //Changed!
            this.spelForm = spelForm; //Changed!
            this.spikScore = spikScore; //Changed!
            this.combinedWinnerScore = combinedWinnerScore; //Changed!
            this.edgeVsSecond = edgeVsSecond; //Changed!
            this.secondName = secondName; //Changed!
            this.combinedSecondScore = combinedSecondScore; //Changed!
            this.historyBoost = historyBoost; //Changed!
            this.starters = starters; //Changed!
        } //Changed!
    } //Changed!

    @Tool( //Changed!
            name = "pick_spikar_all_tracks_using_history", //Changed!
            description = "Välj N spikar globalt över alla banor för ett datum+spelform, baserat på predict_day_all_tracks_using_history. Tar top2 per lopp och rankar på spikScore = combinedWinnerScore + (combinedWinnerScore - combinedSecondScore)." //Changed!
    ) //Changed!
    public List<GlobalSpikSuggestionWithHistory> pickSpikarAllTracksUsingHistory( //Changed!
                                                                                  String dateOrPhrase, String spelFormOrPhrase, Integer count, Integer historyLimitPerHorse //Changed!
    ) { //Changed!
        if (count == null || count <= 0) count = 2; //Changed!
        if (historyLimitPerHorse == null || historyLimitPerHorse <= 0) historyLimitPerHorse = 200; //Changed!

        Integer targetDate = parseDateFlexible(dateOrPhrase); //Changed!
        if (targetDate == null) return List.of(); //Changed!

        String form = parseSpelFormFlexible(spelFormOrPhrase); //Changed!
        if (isAggregatorSpelForm(form)) form = "vinnare"; //Changed!
        if (form == null) form = "vinnare"; //Changed!

        DayPredictionWithHistory day = predictDayAllTracksUsingHistory( //Changed!
                String.valueOf(targetDate), form, 2, historyLimitPerHorse //Changed!
        ); //Changed!
        if (day == null || day.tracks == null || day.tracks.isEmpty()) return List.of(); //Changed!

        List<GlobalSpikSuggestionWithHistory> candidates = new ArrayList<>(); //Changed!

        for (TrackPredictionWithHistory t : day.tracks) { //Changed!
            if (t == null || t.laps == null) continue; //Changed!
            for (LapPredictionWithHistory l : t.laps) { //Changed!
                if (l == null || l.top == null || l.top.isEmpty()) continue; //Changed!

                WinnerWithHistory first = l.top.get(0); //Changed!
                WinnerWithHistory second = (l.top.size() > 1) ? l.top.get(1) : null; //Changed!

                double w1 = first.combinedScore; //Changed!
                double w2 = (second == null) ? w1 : second.combinedScore; //Changed!
                double edge = w1 - w2; //Changed!
                double spikScore = w1 + edge; //Changed!

                WinnerSuggestion pick = first.pick; //Changed!
                if (pick == null) continue; //Changed!

                candidates.add(new GlobalSpikSuggestionWithHistory( //Changed!
                        pick.name, pick.banKod, pick.lap, pick.startDate, pick.spelForm, //Changed!
                        spikScore, w1, edge, //Changed!
                        (second == null ? null : second.pick.name), //Changed!
                        (second == null ? null : w2), //Changed!
                        first.historyBoost, pick.starters //Changed!
                )); //Changed!
            } //Changed!
        } //Changed!

        return candidates.stream() //Changed!
                .sorted((a, b) -> Double.compare(b.spikScore, a.spikScore)) //Changed!
                .limit(count) //Changed!
                .toList(); //Changed!
    } //Changed!

}
