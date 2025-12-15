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

    public static class DaySnapshot {
        public Integer startDate;
        public String requestedSpelForm;
        public List<TrackSnapshot> tracks;
        public DaySnapshot(Integer startDate, String requestedSpelForm, List<TrackSnapshot> tracks) {
            this.startDate = startDate;
            this.requestedSpelForm = requestedSpelForm;
            this.tracks = tracks;
        }
    }

    public static class TrackSnapshot {
        public String banKod;
        public String spelFormUsed;
        public List<LapSnapshot> laps;
        public TrackSnapshot(String banKod, String spelFormUsed, List<LapSnapshot> laps) {
            this.banKod = banKod;
            this.spelFormUsed = spelFormUsed;
            this.laps = laps;
        }
    }

    public static class LapSnapshot {
        public String lap;
        public List<Integer> starters;
        public LapSnapshot(String lap, List<Integer> starters) {
            this.lap = lap;
            this.starters = starters;
        }
    }

    @Tool(
            name = "snapshot_by_date_form_all_tracks",
            description = "Returnerar alla banor för ett datum, samt alla lopp/avd och vilka starter (0-8) som finns per lopp, filtrerat på spelform (med fallback till vinnare/utan spelform)."
    )
    public DaySnapshot snapshotByDateFormAllTracks(String dateOrPhrase, String spelFormOrPhrase) {
        Integer startDate = parseDateFlexible(dateOrPhrase);
        if (startDate == null) return new DaySnapshot(null, null, List.of());

        String parsedForm = parseSpelFormFlexible(spelFormOrPhrase);
        if (isAggregatorSpelForm(parsedForm)) parsedForm = "vinnare";
        String requestedForm = (parsedForm == null ? "vinnare" : parsedForm);

        List<String> tracks = horseResultRepo.distinctBanKodByDate(startDate);
        List<TrackSnapshot> out = new ArrayList<>();

        for (String banKod : tracks) {
            String formUsed = requestedForm;
            List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, formUsed);

            if (laps.isEmpty() && !"vinnare".equalsIgnoreCase(formUsed)) {
                formUsed = "vinnare";
                laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, formUsed);
            }
            if (laps.isEmpty()) {
                formUsed = null;
                laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, null);
            }

            List<LapSnapshot> lapSnaps = new ArrayList<>();
            for (String lap : laps.stream().sorted(Comparator.comparingInt(TravTools::lapKey)).toList()) {
                List<String> startersRaw = horseResultRepo.distinctStartersByDateBanKodFormLap(startDate, banKod, formUsed, lap);
                List<Integer> starters = (startersRaw == null ? List.<Integer>of() : startersRaw.stream()
                        .map(TravTools::safeInt)
                        .filter(x -> x >= 0 && x <= 8)
                        .distinct()
                        .sorted()
                        .toList());

                lapSnaps.add(new LapSnapshot(lap, starters));
            }

            out.add(new TrackSnapshot(banKod, (formUsed == null ? "ALL" : formUsed), lapSnaps));
        }

        return new DaySnapshot(startDate, requestedForm, out);
    }


    private static boolean isStarterZero(HorseResult r) {
        return safeInt(r.getStarter()) == 0;
    }

    private static List<HorseResult> preferStarterZero(List<HorseResult> rows) {
        if (rows == null || rows.isEmpty()) return rows;

        return rows.stream()
                .sorted(Comparator.comparingInt(r -> isStarterZero(r) ? 0 : 1))
                .toList();
    }

    private static int lapKey(String lap) {
        try { return Integer.parseInt(lap); } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private static double bonusFromPerspectives(
            int avgPrestation, int avgForm, int avgFart, int avgMotstand,
            int avgKlass, int avgSkrik, int avgPlacering
    ) {
        double raw = 0.06 * avgPrestation
                + 0.05 * avgForm
                + 0.04 * avgFart
                + 0.03 * avgMotstand
                + 0.02 * avgKlass
                + 0.01 * avgSkrik
                + 0.01 * avgPlacering;
        return raw * 0.20;
    }


    private static Integer placementTop6FromName(String horseName) {
        if (horseName == null) return null;
        Matcher m = Pattern.compile("\\((\\d)\\)\\s*$").matcher(horseName.trim());
        if (!m.find()) return null;
        int p = Integer.parseInt(m.group(1));
        return (p >= 1 && p <= 6) ? p : null;
    }

    private static String stripPlacementFromName(String horseName) {
        if (horseName == null) return null;
        return horseName.replaceAll("\\s*\\(\\d\\)\\s*$", "").trim();
    }

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

        rows = preferStarterZero(rows);

        if (rows.isEmpty() || aggregator) {
            effectiveForm = "vinnare";
            rows = horseResultRepo.findByStartDateAndBanKodAndLapAndSpelFormIgnoreCase(startDate, banKod, lap, "vinnare");
            rows = preferStarterZero(rows);
        }

        if (rows.isEmpty()) {
            rows = horseResultRepo.findByStartDateAndBanKodAndLap(startDate, banKod, lap);
            rows = preferStarterZero(rows);
            effectiveForm = "vinnare";
        }

        if (rows.isEmpty()) return List.of();

        final String formForReturn = effectiveForm;

        Map<String, List<HorseResult>> byHorse = rows.stream()
                .collect(Collectors.groupingBy(r -> stripPlacementFromName(r.getNameOfHorse())));

        List<WinnerSuggestion> ranked = byHorse.entrySet().stream().map(e -> {
                    String baseName = e.getKey();
                    List<HorseResult> list = e.getValue();

                    Integer placement = list.stream()
                            .map(r -> placementTop6FromName(r.getNameOfHorse()))
                            .filter(Objects::nonNull)
                            .min(Integer::compareTo)
                            .orElse(null);

                    String displayName = (placement == null) ? baseName : (baseName + " (" + placement + ")");

                    List<Integer> starters = new ArrayList<>();
                    List<Integer> analys = new ArrayList<>();
                    List<Integer> prest = new ArrayList<>();
                    List<Integer> tid = new ArrayList<>();
                    List<Integer> motst = new ArrayList<>();

                    List<Integer> klass = new ArrayList<>();
                    List<Integer> skrik = new ArrayList<>();
                    List<Integer> plac = new ArrayList<>();
                    List<Integer> formv = new ArrayList<>();

                    for (HorseResult r : list) {
                        int s = safeInt(r.getStarter());
                        starters.add(s);

                        analys.add(safeInt(r.getProcentAnalys()));
                        prest.add(safeInt(r.getProcentPrestation()));
                        tid.add(safeInt(r.getProcentFart()));
                        motst.add(safeInt(r.getProcentMotstand()));

                        klass.add(safeInt(r.getKlassProcent()));
                        skrik.add(safeInt(r.getProcentSkrik()));
                        plac.add(safeInt(r.getProcentPlacering()));
                        formv.add(safeInt(r.getProcentForm()));
                    }

                    double sumW = 0;
                    double sumAnalys = 0, sumPrest = 0, sumTid = 0, sumMot = 0;
                    double sumKlass = 0, sumSkrik = 0, sumPlac = 0, sumForm = 0;

                    for (int i = 0; i < starters.size(); i++) {
                        double w = starterWeight(starters.get(i));
                        sumW += w;

                        sumAnalys += w * analys.get(i);
                        sumPrest += w * prest.get(i);
                        sumTid += w * tid.get(i);
                        sumMot += w * motst.get(i);

                        sumKlass += w * klass.get(i);
                        sumSkrik += w * skrik.get(i);
                        sumPlac  += w * plac.get(i);
                        sumForm  += w * formv.get(i);
                    }

                    double wAvgAnalys = sumAnalys / sumW;

                    double mean = analys.stream().mapToDouble(a -> a).average().orElse(0);
                    double var = analys.stream().mapToDouble(a -> (a - mean) * (a - mean)).average().orElse(0);
                    double std = Math.sqrt(var);

                    int avgA = (int) Math.round(sumAnalys / sumW);
                    int avgP = (int) Math.round(sumPrest / sumW);
                    int avgT = (int) Math.round(sumTid / sumW);
                    int avgM = (int) Math.round(sumMot / sumW);

                    int avgK = (int) Math.round(sumKlass / sumW);
                    int avgS = (int) Math.round(sumSkrik / sumW);
                    int avgPl = (int) Math.round(sumPlac / sumW);
                    int avgF = (int) Math.round(sumForm / sumW);

                    double baseScore = wAvgAnalys - 0.5 * std;
                    double bonus = bonusFromPerspectives(avgP, avgF, avgT, avgM, avgK, avgS, avgPl);
                    double score = baseScore + bonus;

                    String startersStr = starters.stream().sorted().map(String::valueOf).distinct()
                            .collect(Collectors.joining(","));

                    return new WinnerSuggestion(
                            displayName, banKod, lap, startDate, formForReturn,
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
        rows = onlyStarterZeroOrAllIfMissing(rows);
        rows = preferStarterZero(rows);
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
            field = onlyStarterZeroOrAllIfMissing(field);
            field = preferStarterZero(field);
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

    public static class LoppTopN {
        public String lap;
        public List<WinnerSuggestion> top;
        public LoppTopN(String lap, List<WinnerSuggestion> top) { this.lap = lap; this.top = top; }
    }

    @Tool(name = "top_by_day_track_form",
            description = "Topp N per lopp/avd för ett datum + bana + spelform. Prioriterar starter=0 om finns i avdelningen, annars väger den ihop starter 1-8.")
    public List<LoppTopN> topByDayTrackForm(String dateOrPhrase, String banKodOrTrack, String spelFormOrPhrase, Integer topN) {

        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String form = parseSpelFormFlexible(spelFormOrPhrase);
        if (topN == null || topN <= 0) topN = 3;
        if (startDate == null || banKod == null) return List.of();

        boolean aggregator = isAggregatorSpelForm(form);
        String effectiveForm = (form == null ? "vinnare" : form);

        List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, effectiveForm);

        if (laps.isEmpty() || aggregator) {
            effectiveForm = "vinnare";
            laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, "vinnare");
        }
        if (laps.isEmpty()) {
            laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, null);
        }

        final String formForCalls = effectiveForm;

        Integer finalTopN = topN;
        return laps.stream()
                .sorted(Comparator.comparingInt(TravTools::lapKey))
                .map(lap -> new LoppTopN(
                        lap,
                        pickWinnerAcrossStarters(String.valueOf(startDate), banKod, lap, formForCalls, finalTopN)
                ))
                .toList();
    }

    @Tool(name = "top_by_day_phrase",
            description = "Som top_by_day_track_form men tar en svensk fras. Ex: 'Visa topp 3 i alla avdelningar på Solvalla 2025-12-03 spelform vinnare'")
    public List<LoppTopN> topByDayPhrase(String phrase, Integer topN) {
        return topByDayTrackForm(phrase, phrase, phrase, topN);
    }

    public static class GlobalSpikSuggestion {
        public String name;
        public String banKod;
        public String lap;
        public Integer startDate;
        public String spelForm;
        public double spikScore;
        public double winnerScore;
        public double edgeVsSecond;
        public String secondName;
        public Double secondScore;
        public String starters;

        public GlobalSpikSuggestion() { }

        public GlobalSpikSuggestion(String name, String banKod, String lap, Integer startDate, String spelForm,
                                    double spikScore, double winnerScore, double edgeVsSecond,
                                    String secondName, Double secondScore, String starters) {
            this.name = name;
            this.banKod = banKod;
            this.lap = lap;
            this.startDate = startDate;
            this.spelForm = spelForm;
            this.spikScore = spikScore;
            this.winnerScore = winnerScore;
            this.edgeVsSecond = edgeVsSecond;
            this.secondName = secondName;
            this.secondScore = secondScore;
            this.starters = starters;
        }
    }

    @Tool(
            name = "pick_spikar_all_tracks_by_date_form",
            description = "Välj N spikar globalt över alla banor för ett datum + spelform. Tar top2 per lopp och rankar på spikScore = winnerScore + (winnerScore - secondScore)."
    )
    public List<GlobalSpikSuggestion> pickSpikarAllTracksByDateForm(String dateOrPhrase, String spelFormOrPhrase, Integer count) {
        if (count == null || count <= 0) count = 5;

        DaySnapshot snap = snapshotByDateFormAllTracks(dateOrPhrase, spelFormOrPhrase);
        if (snap == null || snap.startDate == null || snap.tracks == null || snap.tracks.isEmpty()) return List.of();

        List<GlobalSpikSuggestion> candidates = new ArrayList<>();

        for (TrackSnapshot t : snap.tracks) {
            if (t == null || t.laps == null) continue;
            for (LapSnapshot l : t.laps) {
                if (l == null || l.lap == null) continue;

                List<WinnerSuggestion> top2 = pickWinnerAcrossStarters(
                        String.valueOf(snap.startDate),
                        t.banKod,
                        l.lap,
                        spelFormOrPhrase,
                        2
                );

                if (top2 == null || top2.isEmpty()) continue;

                WinnerSuggestion first = top2.get(0);
                WinnerSuggestion second = (top2.size() > 1) ? top2.get(1) : null;

                double edge = (second == null) ? 0.0 : (first.score - second.score);
                double spikScore = first.score + edge;

                candidates.add(new GlobalSpikSuggestion(
                        first.name,
                        first.banKod,
                        first.lap,
                        first.startDate,
                        first.spelForm,
                        spikScore,
                        first.score,
                        edge,
                        (second == null ? null : second.name),
                        (second == null ? null : second.score),
                        first.starters
                ));
            }
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.spikScore, a.spikScore))
                .limit(count)
                .toList();
    }

    @Tool(name = "pick_spikar_across_laps",
            description = "Välj N spikar (vinnare) från olika lopp/avd för datum+bana+spelform. Tar bästa top1 per avd och väljer sedan de N starkaste.")
    public List<WinnerSuggestion> pickSpikarAcrossLaps(String dateOrPhrase, String banKodOrTrack, String spelFormOrPhrase, Integer count) {

        if (count == null || count <= 0) count = 2;

        List<LoppTopN> perLap = topByDayTrackForm(dateOrPhrase, banKodOrTrack, spelFormOrPhrase, 2);

        return perLap.stream()
                .map(x -> {
                    if (x.top == null || x.top.isEmpty()) return null;
                    WinnerSuggestion first = x.top.get(0);
                    WinnerSuggestion second = (x.top.size() > 1) ? x.top.get(1) : null;
                    double edge = (second == null) ? 0.0 : (first.score - second.score);
                    double spikScore = first.score + edge;
                    return new WinnerSuggestion(
                            first.name, first.banKod, first.lap, first.startDate, first.spelForm,
                            spikScore, first.variants, first.starters,
                            first.avgAnalys, first.avgPrestation, first.avgTid, first.avgMotstand
                    );
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(count)
                .toList();
    }

    @Tool(name = "pick_spikar_by_phrase",
            description = "Tolka svensk fras och välj 2 spikar från olika avdelningar. Ex: 'Ge mig 2 spikar på Solvalla 2025-12-03 spelform vinnare'")
    public List<WinnerSuggestion> pickSpikarByPhrase(String phrase, Integer count) {
        return pickSpikarAcrossLaps(phrase, phrase, phrase, count);
    }

    public static class HistoryStats {
        public String horse;
        public int races;
        public int top6;
        public int wins;
        public double winRate;
        public double top6Rate;
        public Double avgPlacementTop6;
        public Integer lastDate;

        public HistoryStats() {}

        public HistoryStats(String horse, int races, int top6, int wins, double winRate, double top6Rate, Double avgPlacementTop6, Integer lastDate) {
            this.horse = horse;
            this.races = races;
            this.top6 = top6;
            this.wins = wins;
            this.winRate = winRate;
            this.top6Rate = top6Rate;
            this.avgPlacementTop6 = avgPlacementTop6;
            this.lastDate = lastDate;
        }
    }

    public static class WinnerWithHistory {
        public WinnerSuggestion pick;
        public double combinedScore;
        public double historyBoost;
        public HistoryStats history;

        public WinnerWithHistory() {}

        public WinnerWithHistory(WinnerSuggestion pick, double combinedScore, double historyBoost, HistoryStats history) {
            this.pick = pick;
            this.combinedScore = combinedScore;
            this.historyBoost = historyBoost;
            this.history = history;
        }
    }

    public static class LapPredictionWithHistory {
        public String lap;
        public List<WinnerWithHistory> top;
        public LapPredictionWithHistory() {}
        public LapPredictionWithHistory(String lap, List<WinnerWithHistory> top) {
            this.lap = lap;
            this.top = top;
        }
    }

    public static class TrackPredictionWithHistory {
        public String banKod;
        public String spelFormUsed;
        public List<LapPredictionWithHistory> laps;
        public TrackPredictionWithHistory() {}
        public TrackPredictionWithHistory(String banKod, String spelFormUsed, List<LapPredictionWithHistory> laps) {
            this.banKod = banKod;
            this.spelFormUsed = spelFormUsed;
            this.laps = laps;
        }
    }

    public static class DayPredictionWithHistory {
        public Integer startDate;
        public String requestedSpelForm;
        public List<TrackPredictionWithHistory> tracks;
        public DayPredictionWithHistory() {}
        public DayPredictionWithHistory(Integer startDate, String requestedSpelForm, List<TrackPredictionWithHistory> tracks) {
            this.startDate = startDate;
            this.requestedSpelForm = requestedSpelForm;
            this.tracks = tracks;
        }
    }

    @Tool(
            name = "predict_day_all_tracks_using_history",
            description = "För ett givet datum+spelform: listar alla banor+lopp som finns (snapshot), plockar toppkandidater per lopp och justerar rankingen med historik före datumet över ALLA banor. Historik tolkar topp6 via '(1)-(6)' i hästnamn."
    )
    public DayPredictionWithHistory predictDayAllTracksUsingHistory(String dateOrPhrase, String spelFormOrPhrase, Integer topN, Integer historyLimitPerHorse) {
        Integer targetDate = parseDateFlexible(dateOrPhrase);
        if (targetDate == null) return new DayPredictionWithHistory(null, null, List.of());

        if (topN == null || topN <= 0) topN = 3;
        if (historyLimitPerHorse == null || historyLimitPerHorse <= 0) historyLimitPerHorse = 2000;

        String form = parseSpelFormFlexible(spelFormOrPhrase);
        if (isAggregatorSpelForm(form)) form = "vinnare";
        String requestedForm = (form == null ? "vinnare" : form);

        DaySnapshot snap = snapshotByDateFormAllTracks(String.valueOf(targetDate), requestedForm);
        if (snap == null || snap.tracks == null || snap.tracks.isEmpty()) {
            return new DayPredictionWithHistory(targetDate, requestedForm, List.of());
        }

        Map<String, HistoryStats> historyCache = new HashMap<>();
        List<TrackPredictionWithHistory> outTracks = new ArrayList<>();

        for (TrackSnapshot t : snap.tracks) {
            if (t == null || t.laps == null) continue;

            List<LapPredictionWithHistory> outLaps = new ArrayList<>();

            for (LapSnapshot l : t.laps) {
                if (l == null || l.lap == null) continue;

                int candidateN = Math.max(topN * 2, 6);

                List<WinnerSuggestion> candidates = pickWinnerAcrossStarters(
                        String.valueOf(targetDate),
                        t.banKod,
                        l.lap,
                        requestedForm,
                        candidateN
                );

                if (candidates == null || candidates.isEmpty()) {
                    outLaps.add(new LapPredictionWithHistory(l.lap, List.of()));
                    continue;
                }

                List<WinnerWithHistory> ranked = new ArrayList<>();

                for (WinnerSuggestion w : candidates) {
                    if (w == null || w.name == null) continue;

                    String baseName = stripPlacementFromName(w.name);
                    if (baseName == null || baseName.isBlank()) continue;

                    Integer finalHistoryLimitPerHorse = historyLimitPerHorse;
                    HistoryStats hs = historyCache.computeIfAbsent(
                            normalize(baseName),
                            k -> buildHistoryStatsForHorse(baseName, targetDate, requestedForm, finalHistoryLimitPerHorse)
                    );

                    double boost = scoreBoostFromHistory(hs);
                    double combined = w.score + boost;

                    ranked.add(new WinnerWithHistory(w, combined, boost, hs));
                }

                List<WinnerWithHistory> top = ranked.stream()
                        .sorted((a, b) -> Double.compare(b.combinedScore, a.combinedScore))
                        .limit(topN)
                        .toList();

                outLaps.add(new LapPredictionWithHistory(l.lap, top));
            }

            outTracks.add(new TrackPredictionWithHistory(t.banKod, t.spelFormUsed, outLaps));
        }

        return new DayPredictionWithHistory(targetDate, requestedForm, outTracks);
    }

    private HistoryStats buildHistoryStatsForHorse(String baseName, Integer beforeDate, String spelForm, int limit) {
        if (baseName == null || baseName.isBlank() || beforeDate == null) {
            return new HistoryStats(baseName, 0, 0, 0, 0, 0, null, null);
        }

        List<HorseResult> rows = horseResultRepo.findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(baseName);
        if (rows == null || rows.isEmpty()) {
            return new HistoryStats(baseName, 0, 0, 0, 0, 0, null, null);
        }

        int races = 0, top6 = 0, wins = 0;
        double sumPlacement = 0;
        int placementCount = 0;
        Integer lastDate = null;

        Set<String> seenRace = new HashSet<>();

        for (HorseResult r : rows) {
            if (r == null) continue;

            Integer d = r.getStartDate();
            if (d == null || d >= beforeDate) continue;

            String rowBase = stripPlacementFromName(r.getNameOfHorse());
            if (rowBase == null || !rowBase.equalsIgnoreCase(baseName)) continue;

            if (spelForm != null && r.getSpelForm() != null) {
                if (!normalize(r.getSpelForm()).equals(normalize(spelForm))) continue;
            }

            String raceKey = d + "|" + r.getBanKod() + "|" + r.getLap();
            if (!seenRace.add(raceKey)) continue;

            races++;
            if (lastDate == null) lastDate = d;

            Integer p = placementTop6FromName(r.getNameOfHorse());
            if (p != null) {
                top6++;
                if (p == 1) wins++;
                sumPlacement += p;
                placementCount++;
            }

            if (races >= limit) break;
        }

        double winRate = (races == 0) ? 0.0 : (wins / (double) races);
        double top6Rate = (races == 0) ? 0.0 : (top6 / (double) races);
        Double avgP = (placementCount == 0) ? null : (sumPlacement / placementCount);

        return new HistoryStats(baseName, races, top6, wins, winRate, top6Rate, avgP, lastDate);
    }

    private static double scoreBoostFromHistory(HistoryStats hs) {
        if (hs == null || hs.races <= 0) return 0.0;

        double boost = 15.0 * hs.winRate + 5.0 * hs.top6Rate;

        if (hs.avgPlacementTop6 != null) {
            boost += Math.max(0.0, (7.0 - hs.avgPlacementTop6) * 0.5);
        }

        return boost;
    }

    // Helpers

    private static final double STARTER_ZERO_WEIGHT = 3.0;

    private static double starterWeight(int starter) {
        if (starter <= 0) return STARTER_ZERO_WEIGHT;
        return Math.sqrt(starter);
    }

    private static int safeInt(String s) {
        if (s == null) return 0;
        try {
            String cleaned = s.replaceAll("[^0-9-]", "");
            if (cleaned.isBlank() || "-".equals(cleaned)) return 0;
            return Integer.parseInt(cleaned);
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
            } catch (Exception ignored) { }
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

        public WinnerSuggestion() { }

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

    public static class GlobalSpikSuggestionWithHistory {
        public String name;
        public String banKod;
        public String lap;
        public Integer startDate;
        public String spelForm;
        public double spikScore;
        public double combinedWinnerScore;
        public double edgeVsSecond;
        public String secondName;
        public Double combinedSecondScore;
        public double historyBoost;
        public String starters;

        public GlobalSpikSuggestionWithHistory() {}

        public GlobalSpikSuggestionWithHistory(
                String name, String banKod, String lap, Integer startDate, String spelForm,
                double spikScore, double combinedWinnerScore, double edgeVsSecond,
                String secondName, Double combinedSecondScore, double historyBoost, String starters
        ) {
            this.name = name;
            this.banKod = banKod;
            this.lap = lap;
            this.startDate = startDate;
            this.spelForm = spelForm;
            this.spikScore = spikScore;
            this.combinedWinnerScore = combinedWinnerScore;
            this.edgeVsSecond = edgeVsSecond;
            this.secondName = secondName;
            this.combinedSecondScore = combinedSecondScore;
            this.historyBoost = historyBoost;
            this.starters = starters;
        }
    }

    @Tool(
            name = "pick_spikar_all_tracks_using_history",
            description = "Välj N spikar globalt över alla banor för ett datum+spelform, baserat på predict_day_all_tracks_using_history. Tar top2 per lopp och rankar på spikScore = combinedWinnerScore + (combinedWinnerScore - combinedSecondScore)."
    )
    public List<GlobalSpikSuggestionWithHistory> pickSpikarAllTracksUsingHistory(
            String dateOrPhrase, String spelFormOrPhrase, Integer count, Integer historyLimitPerHorse
    ) {
        if (count == null || count <= 0) count = 2;
        if (historyLimitPerHorse == null || historyLimitPerHorse <= 0) historyLimitPerHorse = 200;

        Integer targetDate = parseDateFlexible(dateOrPhrase);
        if (targetDate == null) return List.of();

        String form = parseSpelFormFlexible(spelFormOrPhrase);
        if (isAggregatorSpelForm(form)) form = "vinnare";
        if (form == null) form = "vinnare";

        DayPredictionWithHistory day = predictDayAllTracksUsingHistory(
                String.valueOf(targetDate), form, 2, historyLimitPerHorse
        );
        if (day == null || day.tracks == null || day.tracks.isEmpty()) return List.of();

        List<GlobalSpikSuggestionWithHistory> candidates = new ArrayList<>();

        for (TrackPredictionWithHistory t : day.tracks) {
            if (t == null || t.laps == null) continue;
            for (LapPredictionWithHistory l : t.laps) {
                if (l == null || l.top == null || l.top.isEmpty()) continue;

                WinnerWithHistory first = l.top.get(0);
                WinnerWithHistory second = (l.top.size() > 1) ? l.top.get(1) : null;

                double w1 = first.combinedScore;
                double w2 = (second == null) ? w1 : second.combinedScore;
                double edge = w1 - w2;
                double spikScore = w1 + edge;

                WinnerSuggestion pick = first.pick;
                if (pick == null) continue;

                candidates.add(new GlobalSpikSuggestionWithHistory(
                        pick.name, pick.banKod, pick.lap, pick.startDate, pick.spelForm,
                        spikScore, w1, edge,
                        (second == null ? null : (second.pick == null ? null : second.pick.name)),
                        (second == null ? null : w2),
                        first.historyBoost, pick.starters
                ));
            }
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.spikScore, a.spikScore))
                .limit(count)
                .toList();
    }

    private static List<HorseResult> onlyStarterZeroOrAllIfMissing(List<HorseResult> rows) {
        if (rows == null || rows.isEmpty()) return rows;
        List<HorseResult> zero = rows.stream().filter(TravTools::isStarterZero).toList();
        return zero.isEmpty() ? rows : zero;
    }
}
