package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.HorseResult;
import org.example.amortizationhelper.Entity.Roi;
import org.example.amortizationhelper.repo.HorseResultRepo;
import org.example.amortizationhelper.repo.RoiRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Function;

@Component
@AllArgsConstructor
public class TravTools {

    private final HorseResultRepo horseResultRepo;
    private final RoiRepo roiRepo;

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
            description = "Returnerar banor med data för exakt valt datum och spelform, deras faktiska loppnummer och tillgängliga analysfönster (starter). Ingen avdelningsmappning."
    )
    public DaySnapshot snapshotByDateFormAllTracks(String dateOrPhrase, String spelFormOrPhrase) {
        Integer startDate = parseDateFlexible(dateOrPhrase);
        if (startDate == null) return new DaySnapshot(null, null, List.of());

        String parsedForm = parseSpelFormFlexible(spelFormOrPhrase);
        String requestedForm = (parsedForm == null ? "vinnare" : parsedForm);

        List<String> tracks = horseResultRepo.distinctBanKodByDate(startDate);
        List<TrackSnapshot> out = new ArrayList<>();

        for (String banKod : tracks) {
            String formUsed = requestedForm;
            List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, formUsed);

            if (laps.isEmpty()) continue;

            List<LapSnapshot> lapSnaps = new ArrayList<>();
            for (String lap : laps.stream().sorted(Comparator.comparingInt(TravTools::lapKey)).toList()) {
                List<String> startersRaw = horseResultRepo.distinctStartersByDateBanKodFormLap(startDate, banKod, formUsed, lap);
                List<Integer> starters = (startersRaw == null ? List.<Integer>of() : startersRaw.stream()
                        .map(TravQueryParser::starter)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList());

                lapSnaps.add(new LapSnapshot(lap, starters));
            }

            out.add(new TrackSnapshot(banKod, (formUsed == null ? "ALL" : formUsed), lapSnaps));
        }

        return new DaySnapshot(startDate, requestedForm, out);
    }

    private static boolean isStarterZero(HorseResult row) {
        return row != null && "0".equals(parseStarterFlexible(row.getStarter()));
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
            Double avgPrestation, Double avgForm, Double avgFart, Double avgMotstand,
            Double avgKlass, Double avgSkrik, Double avgPlacering
    ) {
        double raw = 0.06 * ( avgPrestation == null ? 0 : avgPrestation)
                + 0.05 * ( avgForm == null ? 0 : avgForm)
                + 0.04 * ( avgFart == null ? 0 : avgFart)
                + 0.03 * ( avgMotstand == null ? 0 : avgMotstand)
                + 0.02 * ( avgKlass == null ? 0 : avgKlass)
                + 0.01 * ( avgSkrik == null ? 0 : avgSkrik)
                + 0.01 * ( avgPlacering == null ? 0 : avgPlacering);
        return raw * 0.20;
    }

    private static String stripPlacementFromName(String horseName) {
        if (horseName == null) return null;
        return horseName.replaceAll("\\s*\\(\\d{1,2}\\)\\s*$", "").trim();
    }

    @Tool(description = "Hämta analysvärden från rank via id. Detta är inte ett tävlingsresultat.")
    public HorseResult getHorseValues(Long id) {
        return id == null ? null : horseResultRepo.findById(id).orElse(null);
    }

    @Tool(name = "race_results_by_date_track_lap", description = "Hämta registrerade tävlingsresultat från roi.resultat kopplat till rank för datum, bana, faktiskt loppnummer och spelform (tomt = vinnare). Använd för vem som vann/placeringar, inte prognoser. Dubbletter mellan analysfönster slås ihop; saknade, nollkodade och motstridiga resultat markeras uttryckligen. Resultaten är lagrade uppgifter, inte garanterad liveinformation eller fullständig officiell resultatlista. Avdelningsnummer måste först kopplas till faktiskt lopp.")
    public RaceResults raceResultsByDateTrackLap(String dateOrPhrase, String banKodOrTrack,
                                               String lapOrPhrase, String spelFormOrNull) {
        Integer date = parseDateFlexible(dateOrPhrase);
        String track = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapFlexible(lapOrPhrase);
        String form = parseSpelFormFlexible(spelFormOrNull);
        if (form == null) form = "vinnare";
        if (date == null || track == null || lap == null) {
            return new RaceResults(date, track, lap, form, "INVALID_QUERY", "Ange ett giltigt datum, en entydig bana och faktiskt loppnummer. Avdelning är inte samma sak som loppnummer.", List.of());
        }
        List<HorseResult> field = horseResultRepo.findField(date, track, lap, form);
        if (field.isEmpty()) {
            return new RaceResults(date, track, lap, form, "NO_DATA", "Inga lagrade hästar för exakt detta datum, bana, lopp och spelform. Det betyder inte att loppet inte körts.", List.of());
        }
        List<Long> ids = field.stream().map(HorseResult::getId).filter(Objects::nonNull).toList();
        Map<Long, List<Roi>> byRank = ids.isEmpty() ? Map.of() : roiRepo.findByRankIdIn(ids).stream()
                .collect(Collectors.groupingBy(Roi::getRankId));
        Map<String, List<HorseResult>> horses = field.stream().collect(Collectors.groupingBy(TravTools::horseKey));
        List<RaceResultRow> results = new ArrayList<>();
        for (List<HorseResult> variants : horses.values()) {
            HorseResult horse = variants.get(0);
            List<Integer> reported = variants.stream().flatMap(r -> byRank.getOrDefault(r.getId(), List.of()).stream())
                    .map(Roi::getResultat).filter(Objects::nonNull).distinct().sorted().toList();
            List<Integer> positions = reported.stream().filter(p -> p > 0 && p <= 30).toList();
            boolean conflicting = positions.size() > 1;
            Integer placement = positions.size() == 1 ? positions.get(0) : null;
            results.add(new RaceResultRow(horse.getNumberOfHorse(), stripPlacementFromName(horse.getNameOfHorse()),
                    placement, conflicting ? "CONFLICTING" : placement == null ? "UNAVAILABLE" : "RECORDED", reported));
        }
        results.sort(Comparator.comparing(RaceResultRow::placement, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(RaceResultRow::numberOfHorse, Comparator.nullsLast(Integer::compareTo)));
        long recorded = results.stream().filter(r -> r.placement() != null).count();
        String status = results.stream().anyMatch(r -> "CONFLICTING".equals(r.status())) ? "CONFLICTING"
                : recorded == 0 ? "NO_RECORDED_RESULTS" : recorded < results.size() ? "PARTIAL" : "RECORDED";
        return new RaceResults(date, track, lap, form, status,
                "Källa: lagrade roi.resultat via rank. Saknad eller nollkodad placering får inte tolkas som förlust, strykning eller att loppet ännu inte körts. Vid konflikt behövs kontroll mot aktuell resultatkälla.", results);
    }

    public record RaceResults(Integer startDate, String banKod, String lap, String spelForm,
                              String status, String sourceNote, List<RaceResultRow> horses) { }

    public record RaceResultRow(Integer numberOfHorse, String name, Integer placement,
                                String status, List<Integer> reportedValues) { }

    @Tool(description = "Lista analysvärden från rank för ett datum och en bana, inte målgång eller spelbolagsodds. Accepterar svenska datum (t.ex. '17 juli 2025') och bannamn (t.ex. 'Solvalla') eller bankod (t.ex. 'S').")
    public List<HorseResult> listByDateAndTrackFlexible(String dateOrPhrase, String banKodOrTrack) {
        Integer start = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        if (start == null || banKod == null) return List.of();

        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKod(start, banKod);
        System.out.println("Tool listByDateAndTrackFlexible hittade " + results.size() + " rader (date=" + start + ", banKod=" + banKod + ")");
        return results;
    }

    @Tool(name = "results_by_date_track_lap",
            description = "Analysvärden (Analys/Prestation/Motstånd/Tid) för datum, bana och faktiskt loppnummer. Detta verktyg visar inte tävlingsresultat; använd race_results_by_date_track_lap för placeringar. Accepterar svenska datum, bannamn/bankod och t.ex. 'lopp 5' eller '5'.")
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
        return topByField(dateOrPhrase, banKodOrTrack, lapOrPhrase, "vinnare", limit);
    }

    @Tool(name = "pick_winner_across_starters",
            description = "Ranka vinnarkandidater genom att väga ihop tillgängliga analysfönster för exakt datum, bana, spelform och faktiskt lopp. Bevarar decimaler och okända delvärden. Poäng är en heuristisk ranking, inte vinstsannolikhet eller tävlingsresultat.")
    public List<WinnerSuggestion> pickWinnerAcrossStarters(String dateOrPhrase,
                                                           String banKodOrTrack,
                                                           String lapOrPhrase,
                                                           String spelFormOrPhrase,
                                                           Integer topN) {
        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapFlexible(lapOrPhrase);
        String parsedForm = parseSpelFormFlexible(spelFormOrPhrase);
        topN = boundedLimit(topN, 3, 20);

        if (startDate == null || banKod == null || lap == null) return List.of();

        String effectiveForm = (parsedForm == null ? "vinnare" : parsedForm);

        List<HorseResult> rows = horseResultRepo
                .findByStartDateAndBanKodAndLapAndSpelFormIgnoreCase(startDate, banKod, lap, effectiveForm);

        rows = uniqueVariants(rows).stream()
                .filter(r -> TravQueryParser.percent(r.getProcentAnalys()) != null)
                .filter(r -> TravQueryParser.starter(r.getStarter()) != null)
                .toList();

        if (rows.isEmpty()) return List.of();

        final String formForReturn = effectiveForm;

        Map<String, List<HorseResult>> byHorse = rows.stream()
                .collect(Collectors.groupingBy(TravTools::horseKey));

        List<WinnerSuggestion> ranked = byHorse.entrySet().stream().map(e -> {
                    List<HorseResult> list = e.getValue();
                    String displayName = stripPlacementFromName(list.get(0).getNameOfHorse());

                    List<Integer> starters = list.stream().map(r -> TravQueryParser.starter(r.getStarter())).toList();
                    Double avgA = weightedAverage(list, HorseResult::getProcentAnalys);
                    Double avgP = weightedAverage(list, HorseResult::getProcentPrestation);
                    Double avgT = weightedAverage(list, HorseResult::getProcentFart);
                    Double avgM = weightedAverage(list, HorseResult::getProcentMotstand);
                    Double avgK = weightedAverage(list, HorseResult::getKlassProcent);
                    Double avgS = weightedAverage(list, HorseResult::getProcentSkrik);
                    Double avgPl = weightedAverage(list, HorseResult::getProcentPlacering);
                    Double avgF = weightedAverage(list, HorseResult::getProcentForm);
                    double mean = list.stream().mapToDouble(TravTools::analysisValue).average().orElseThrow();
                    double variance = list.stream().mapToDouble(r -> Math.pow(analysisValue(r) - mean, 2)).average().orElseThrow();
                    double score = avgA - 0.5 * Math.sqrt(variance)
                            + bonusFromPerspectives(avgP, avgF, avgT, avgM, avgK, avgS, avgPl);
                    String startersStr = starters.stream().sorted().map(String::valueOf).distinct()
                            .collect(Collectors.joining(","));

                    WinnerSuggestion suggestion = new WinnerSuggestion(
                            displayName, banKod, lap, startDate, formForReturn,
                            score, starters.size(), startersStr, avgA, avgP, avgT, avgM
                    );
                    suggestion.numberOfHorse = list.get(0).getNumberOfHorse();
                    return suggestion;
                }).sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topN)
                .toList();

        System.out.println("pick_winner_across_starters: date=" + startDate + ", banKod=" + banKod + ", lap=" + lap + ", formIn=" + parsedForm + " -> using=" + formForReturn + " rows=" + rows.size());

        return ranked;
    }

    @Tool(
            name = "pick_winner_by_swedish_phrase",
            description = "Tolka en svensk fras med datum, bana, spelform och lopp (utan antal starter) och välj topp N över alla starter. Ex: 'Vem vinner på Solvalla 2026-09-03 med spelform vinnare i lopp 7?'"
    )
    public List<WinnerSuggestion> pickWinnerBySwedishPhrase(String phrase, Integer topN) {
        topN = boundedLimit(topN, 3, 20);
        return pickWinnerAcrossStarters(phrase, phrase, phrase, phrase, topN);
    }

    @Tool(description = "Sök fram en häst och dess värden baserat på namnet på hästen")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        if (nameFragment == null || nameFragment.isBlank()) return List.of();
        return horseResultRepo.findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(
                nameFragment.trim(), PageRequest.of(0, 100));
    }

    @Tool(description = "Visa en hästs lagrade analysvärden sorterade efter datum (senaste först), högst 100 rader. Analysfönster kan ge flera rader per lopp; detta är inte en fullständig resultathistorik.")
    public List<HorseResult> horseHistory(String nameFragment, Integer limit) {
        if (nameFragment == null || nameFragment.isBlank()) return List.of();
        limit = boundedLimit(limit, 5, 100);
        return horseResultRepo
                .findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(nameFragment.trim(), PageRequest.of(0, limit))
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
        String banKod = toBanKod(phrase);
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
            description = "Hämta hästar för datum, bana, spelform, lopp och antal starter. Tar naturligt datum/bana/lopp (ex. '2026-09-02', 'Axevalla'/'S', 'lopp 3'), spelform (ex. 'vinnare', 'V85') och starter (ex. '5'). Sortera själv i klienten om du vill.")
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
        String banKod = toBanKod(phrase);
        String lap = parseLap(norm);
        String spelForm = parseSpelFormFlexible(norm);
        String starter = parseStarterFlexible(norm);

        if (date == null || banKod == null || lap == null || spelForm == null || starter == null) {
            return List.of();
        }
        return horseResultRepo.findByStartDateAndBanKodAndLapAndSpelFormIgnoreCaseAndStarter(
                date, banKod, lap, spelForm, starter);
    }

    private static Integer parseDateFlexible(String value) { return TravQueryParser.date(value); }

    private static String parseLapOrAvdFlexible(String value) { return TravQueryParser.race(value); }

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

    @Tool(name = "lopp_by_date_track_form", description = "Lista faktiska lopp för datum + bana (+ ev. spelform).")
    public List<String> loppByDateTrackForm(String dateOrPhrase, String banKodOrTrack, String spelFormOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String f = parseSpelFormFlexible(spelFormOrNull);
        if (d == null || b == null) return List.of();
        return horseResultRepo.distinctLapByDateBanKodAndForm(d, b, f);
    }

    @Tool(name = "starters_by_date_track_form_lopp", description = "Lista möjliga starter-värden för datum + bana + spelform + faktiska lopp.")
    public List<String> startersByDateTrackFormLopp(String dateOrPhrase, String banKodOrTrack, String spelFormOrNull, String lapOrAvd) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String f = parseSpelFormFlexible(spelFormOrNull);
        String lap = parseLapOrAvdFlexible(lapOrAvd);
        if (d == null || b == null || lap == null) return List.of();
        return horseResultRepo.distinctStartersByDateBanKodFormLap(d, b, f, lap);
    }

    @Tool(name = "field_sorted", description = "Hämta hela fältet sorterat på Analys (desc) för datum + bana + faktiska lopp (+ ev. spelform).")
    public List<HorseResult> fieldSorted(String dateOrPhrase, String banKodOrTrack, String lapOrAvd, String spelFormOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String lap = parseLapOrAvdFlexible(lapOrAvd);
        String f = parseSpelFormFlexible(spelFormOrNull);
        if (f == null) f = "vinnare";
        if (d == null || b == null || lap == null) return List.of();

        List<HorseResult> rows = horseResultRepo.findField(d, b, lap, f);
        rows = onlyStarterZeroOrAllIfMissing(rows);
        rows = preferStarterZero(rows);
        return rows.stream()
                .sorted(Comparator.comparingDouble(TravTools::analysisValue).reversed())
                .toList();
    }

    @Tool(name = "top_by_field", description = "Topp N i ett fält (datum + bana + faktiska lopp + ev. spelform).")
    public List<HorseResult> topByField(String dateOrPhrase, String banKodOrTrack, String lapOrAvd, String spelFormOrNull, Integer topN) {
        topN = boundedLimit(topN, 3, 20);
        return fieldSorted(dateOrPhrase, banKodOrTrack, lapOrAvd, spelFormOrNull).stream()
                .filter(r -> TravQueryParser.percent(r.getProcentAnalys()) != null)
                .limit(topN)
                .toList();
    }

    @Tool(name = "top_by_field_with_starter", description = "Topp N för ett specifikt starter-värde (datum+bana+spelform+faktiska lopp+starter).")
    public List<HorseResult> topByFieldWithStarter(String dateOrPhrase, String banKodOrTrack, String lapOrAvd, String spelForm, String starter, Integer topN) {
        topN = boundedLimit(topN, 3, 20);
        List<HorseResult> rows = resultsByDateTrackLapFormStarter(dateOrPhrase, banKodOrTrack, lapOrAvd, spelForm, starter);
        return uniqueVariants(rows).stream()
                .filter(r -> TravQueryParser.percent(r.getProcentAnalys()) != null)
                .sorted(Comparator.comparingDouble(TravTools::analysisValue).reversed())
                .limit(topN)
                .toList();
    }

    @Tool(name = "best_per_lopp", description = "Ge bästa häst (högst Analys) per faktiska lopp för datum + bana (+ ev. spelform). Returnerar en rad per lopp.")
    public List<PerLoppBest> bestPerLopp(String dateOrPhrase, String banKodOrTrack, String spelFormOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        String f = parseSpelFormFlexible(spelFormOrNull);
        if (f == null) f = "vinnare";
        if (d == null || b == null) return List.of();

        List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(d, b, f);
        List<PerLoppBest> out = new ArrayList<>();
        for (String lap : laps.stream().sorted(Comparator.comparingInt(TravTools::lapKey)).toList()) {
            List<HorseResult> field = horseResultRepo.findField(d, b, lap, f);
            field = onlyStarterZeroOrAllIfMissing(field);
            field = preferStarterZero(field);
            field = field.stream().sorted(Comparator.comparingDouble(TravTools::analysisValue).reversed()).toList();
            if (!field.isEmpty()) {
                HorseResult top = field.get(0);
                if (TravQueryParser.percent(top.getProcentAnalys()) == null) continue;
                PerLoppBest best = new PerLoppBest(lap, top.getNameOfHorse(), TravQueryParser.percent(top.getProcentAnalys()), top.getNumberOfHorse());
                best.starter = top.getStarter();
                out.add(best);
            }
        }
        return out;
    }

    public static class PerLoppBest {
        public String lap;
        public String name;
        public Double analys;
        public Integer nr;
        public String starter;
        public PerLoppBest(String lap, String name, Double analys, Integer nr) { this.lap = lap; this.name = name; this.analys = analys; this.nr = nr; }
    }

    @Tool(name = "pick_winner_by_phrase_smart", description = "Vinnarförslag från en svensk fras med faktiskt loppnummer. Avdelning måste först kopplas till rätt lopp.")
    public List<WinnerSuggestion> pickWinnerByPhraseSmart(String phrase, Integer topN) {
        return pickWinnerBySwedishPhrase(phrase, topN);
    }

    public static class LoppTopN {
        public String lap;
        public List<HorseResult> top;
        public LoppTopN(String lap, List<HorseResult> top) { this.lap = lap; this.top = top; }
    }

    @Tool(name = "top_by_day_track_form",
            description = "Topp N per faktiska lopp för ett datum + bana + spelform, sorterat på vanlig Analys. Prioriterar starter=0 om den finns.")
    public List<LoppTopN> topByDayTrackForm(String dateOrPhrase, String banKodOrTrack, String spelFormOrPhrase, Integer topN) {

        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String form = parseSpelFormFlexible(spelFormOrPhrase);
        topN = boundedLimit(topN, 3, 20);
        if (startDate == null || banKod == null) return List.of();

        String effectiveForm = (form == null ? "vinnare" : form);

        List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, effectiveForm);

        final String formForCalls = effectiveForm;

        Integer finalTopN = topN;
        return laps.stream()
                .sorted(Comparator.comparingInt(TravTools::lapKey))
                .map(lap -> new LoppTopN(
                        lap,
                        topByField(String.valueOf(startDate), banKod, lap, formForCalls, finalTopN)
                ))
                .filter(race -> !race.top.isEmpty())
                .toList();
    }

    @Tool(name = "top_by_day_phrase",
            description = "Som top_by_day_track_form men tar en svensk fras. Ex: 'Visa topp 3 i alla avdelningar på Solvalla 2025-12-03 spelform vinnare'")
    public List<LoppTopN> topByDayPhrase(String phrase, Integer topN) {
        return topByDayTrackForm(phrase, phrase, phrase, topN);
    }

    public static class GlobalSpikSuggestion {
        public Integer numberOfHorse;
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
        count = boundedLimit(count, 5, 20);

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

                GlobalSpikSuggestion candidate = new GlobalSpikSuggestion(
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
                );
                candidate.numberOfHorse = first.numberOfHorse;
                candidates.add(candidate);
            }
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.spikScore, a.spikScore))
                .limit(count)
                .toList();
    }

    @Tool(name = "pick_spikar_across_laps",
            description = "Välj N spikar (vinnare) från olika faktiska lopp för datum+bana+spelform. Tar bästa top1 per avd och väljer sedan de N starkaste.")
    public List<WinnerSuggestion> pickSpikarAcrossLaps(String dateOrPhrase, String banKodOrTrack, String spelFormOrPhrase, Integer count) {

        count = boundedLimit(count, 2, 20);

        Integer startDate = parseDateFlexible(dateOrPhrase);
        String banKod = resolveBanKodFlexible(banKodOrTrack);
        String form = parseSpelFormFlexible(spelFormOrPhrase);
        if (startDate == null || banKod == null) return List.of();

        String effectiveForm = (form == null ? "vinnare" : form);

        List<String> laps = horseResultRepo.distinctLapByDateBanKodAndForm(startDate, banKod, effectiveForm);

        final String formForCalls = effectiveForm;
        final Integer resolvedStartDate = startDate;
        final String resolvedBanKod = banKod;

        return laps.stream()
                .sorted(Comparator.comparingInt(TravTools::lapKey))
                .map(lap -> {
                    List<WinnerSuggestion> top2 = pickWinnerAcrossStarters(
                            String.valueOf(resolvedStartDate),
                            resolvedBanKod,
                            lap,
                            formForCalls,
                            2
                    );
                    if (top2 == null || top2.isEmpty()) return null;

                    WinnerSuggestion first = top2.get(0);
                    WinnerSuggestion second = (top2.size() > 1) ? top2.get(1) : null;
                    double edge = (second == null) ? 0.0 : (first.score - second.score);
                    double spikScore = first.score + edge;
                    WinnerSuggestion suggestion = new WinnerSuggestion(
                            first.name, first.banKod, first.lap, first.startDate, first.spelForm,
                            spikScore, first.variants, first.starters,
                            first.avgAnalys, first.avgPrestation, first.avgTid, first.avgMotstand
                    );
                    suggestion.numberOfHorse = first.numberOfHorse;
                    return suggestion;
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
        public int recordedResults;
        public int missingResults;
        public String sourceNote = "Begränsat urval av lagrade lopp före frågedatumet; placeringar från roi.resultat. Saknade eller motstridiga resultat ger okänd segerandel. Detta är inte fullständig karriärstatistik.";
        public Double winRate;
        public Double top6Rate;
        public Double avgPlacementTop6;
        public Integer lastDate;

        public HistoryStats() {}

        public HistoryStats(String horse, int races, int top6, int wins, Double winRate, Double top6Rate, Double avgPlacementTop6, Integer lastDate) {
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
            description = "För ett givet datum+spelform: listar alla banor+lopp som finns (snapshot), plockar toppkandidater per lopp och justerar rankingen med historik före datumet över ALLA banor. Historik använder registrerade placeringar från roi.resultat, räknar varje lopp en gång och lämnar segerandel okänd när resultat saknas. Poängen är en heuristisk ranking, inte vinstsannolikhet."
    )
    public DayPredictionWithHistory predictDayAllTracksUsingHistory(String dateOrPhrase, String spelFormOrPhrase, Integer topN, Integer historyLimitPerHorse) {
        Integer targetDate = parseDateFlexible(dateOrPhrase);
        if (targetDate == null) return new DayPredictionWithHistory(null, null, List.of());

        topN = boundedLimit(topN, 3, 20);
        historyLimitPerHorse = boundedLimit(historyLimitPerHorse, 100, 500);

        String form = parseSpelFormFlexible(spelFormOrPhrase);
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
        HistoryStats stats = new HistoryStats(baseName, 0, 0, 0, null, null, null, null);
        if (baseName == null || baseName.isBlank() || beforeDate == null) return stats;

        List<HorseResult> rows = horseResultRepo.historyBefore(baseName, beforeDate,
                PageRequest.of(0, Math.min(limit * 16, 8000))).stream()
                .filter(r -> normalize(stripPlacementFromName(r.getNameOfHorse())).equals(normalize(baseName)))
                .toList();
        if (rows.isEmpty()) return stats;

        Map<String, List<HorseResult>> races = new LinkedHashMap<>();
        for (HorseResult row : rows) {
            String key = row.getStartDate() + "|" + row.getBanKod() + "|" + row.getLap();
            if (!races.containsKey(key) && races.size() >= limit) continue;
            races.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<Long> ids = races.values().stream().flatMap(Collection::stream)
                .map(HorseResult::getId).filter(Objects::nonNull).toList();
        Map<Long, List<Roi>> byRank = ids.isEmpty() ? Map.of() : roiRepo.findByRankIdIn(ids).stream()
                .collect(Collectors.groupingBy(Roi::getRankId));
        double sumTop6 = 0;
        for (List<HorseResult> variants : races.values()) {
            stats.races++;
            if (stats.lastDate == null) stats.lastDate = variants.get(0).getStartDate();
            List<Integer> positions = variants.stream()
                    .flatMap(r -> byRank.getOrDefault(r.getId(), List.of()).stream())
                    .map(Roi::getResultat).filter(Objects::nonNull)
                    .filter(p -> p > 0 && p <= 30).distinct().toList();
            if (positions.size() != 1) {
                stats.missingResults++;
                continue;
            }
            stats.recordedResults++;
            int placement = positions.get(0);
            if (placement == 1) stats.wins++;
            if (placement <= 6) {
                stats.top6++;
                sumTop6 += placement;
            }
        }
        // Unknown outcomes must not be counted as losses or silently removed from a win-rate denominator.
        if (stats.races > 0 && stats.missingResults == 0) {
            stats.winRate = stats.wins / (double) stats.races;
            stats.top6Rate = stats.top6 / (double) stats.races;
        }
        stats.avgPlacementTop6 = stats.top6 == 0 ? null : sumTop6 / stats.top6;
        return stats;
    }

    private static double scoreBoostFromHistory(HistoryStats stats) {
        if (stats == null || stats.races < 5 || stats.winRate == null || stats.top6Rate == null) return 0.0;
        // A heuristic ranking adjustment, never an estimated win probability. Shrink small samples.
        return (15.0 * stats.winRate + 5.0 * stats.top6Rate) * Math.min(1.0, stats.races / 20.0);
    }
    private static final double STARTER_ZERO_WEIGHT = 3.0;

    private static double starterWeight(int starter) {
        if (starter <= 0) return STARTER_ZERO_WEIGHT;
        return Math.sqrt(starter);
    }

    private static String normalize(String value) { return TravQueryParser.normalize(value); }

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

    private static String parseLap(String value) { return TravQueryParser.race(value); }

    private static Integer parseDateFromSwedish(String value) { return TravQueryParser.date(value); }

    private static String resolveBanKodFlexible(String value) { return TravQueryParser.track(value); }

    private static String toBanKod(String value) { return TravQueryParser.track(value); }

    private static String parseSpelFormFlexible(String value) { return TravQueryParser.form(value); }

    private static String parseStarterFlexible(String value) {
        Integer starter = TravQueryParser.starter(value);
        return starter == null ? null : starter.toString();
    }

    private static String parseLapFlexible(String lapOrPhrase) {
        if (lapOrPhrase == null || lapOrPhrase.isBlank()) return null;
        return parseLapOrAvdFlexible(lapOrPhrase);
    }

    public static class WinnerSuggestion {
        public Integer numberOfHorse;
        public String name;
        public String banKod;
        public String lap;
        public Integer startDate;
        public String spelForm;
        public double score;
        public int variants;
        public String starters;
        public Double avgAnalys;
        public Double avgPrestation;
        public Double avgTid;
        public Double avgMotstand;

        public WinnerSuggestion() { }

        public WinnerSuggestion(String name, String banKod, String lap, Integer startDate, String spelForm,
                                double score, int variants, String starters,
                                Double avgAnalys, Double avgPrestation, Double avgTid, Double avgMotstand) {
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
        public Integer numberOfHorse;
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
        count = boundedLimit(count, 2, 20);
        historyLimitPerHorse = boundedLimit(historyLimitPerHorse, 100, 500);

        Integer targetDate = parseDateFlexible(dateOrPhrase);
        if (targetDate == null) return List.of();

        String form = parseSpelFormFlexible(spelFormOrPhrase);
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

                GlobalSpikSuggestionWithHistory candidate = new GlobalSpikSuggestionWithHistory(
                        pick.name, pick.banKod, pick.lap, pick.startDate, pick.spelForm,
                        spikScore, w1, edge,
                        (second == null ? null : (second.pick == null ? null : second.pick.name)),
                        (second == null ? null : w2),
                        first.historyBoost, pick.starters
                );
                candidate.numberOfHorse = pick.numberOfHorse;
                candidates.add(candidate);
            }
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.spikScore, a.spikScore))
                .limit(count)
                .toList();
    }

    private static List<HorseResult> onlyStarterZeroOrAllIfMissing(List<HorseResult> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<HorseResult> unique = uniqueVariants(rows);
        // One common history window keeps a regular ranking comparable and each horse unique.
        Integer selected = unique.stream().map(r -> TravQueryParser.starter(r.getStarter()))
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(null);
        if (unique.stream().anyMatch(TravTools::isStarterZero)) selected = 0;
        final Integer chosenStarter = selected;
        Map<String, HorseResult> field = new LinkedHashMap<>();
        unique.stream().filter(r -> Objects.equals(TravQueryParser.starter(r.getStarter()), chosenStarter))
                .forEach(r -> field.putIfAbsent(horseKey(r), r));
        return new ArrayList<>(field.values());
    }

    private static String horseKey(HorseResult row) {
        return row.getNumberOfHorse() != null ? "nr:" + row.getNumberOfHorse()
                : "name:" + normalize(stripPlacementFromName(row.getNameOfHorse()));
    }

    private static List<HorseResult> uniqueVariants(List<HorseResult> rows) {
        Map<String, HorseResult> unique = new LinkedHashMap<>();
        rows.stream().filter(Objects::nonNull)
                .filter(r -> r.getNameOfHorse() != null && !r.getNameOfHorse().isBlank())
                .sorted(Comparator.comparing(HorseResult::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(r -> unique.putIfAbsent(horseKey(r) + "|" + r.getStarter(), r));
        return new ArrayList<>(unique.values());
    }

    private static double analysisValue(HorseResult row) {
        Double value = TravQueryParser.percent(row.getProcentAnalys());
        return value == null ? -1 : value;
    }

    private static Double weightedAverage(List<HorseResult> rows, Function<HorseResult, String> metric) {
        double weighted = 0;
        double weights = 0;
        for (HorseResult row : rows) {
            Double value = TravQueryParser.percent(metric.apply(row));
            Integer starter = TravQueryParser.starter(row.getStarter());
            if (value == null || starter == null) continue;
            double weight = starterWeight(starter);
            weighted += value * weight;
            weights += weight;
        }
        return weights == 0 ? null : Math.round(weighted / weights * 100.0) / 100.0;
    }

    private static int boundedLimit(Integer value, int fallback, int maximum) {
        return value == null || value <= 0 ? fallback : Math.min(value, maximum);
    }
}
