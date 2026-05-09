package org.example.amortizationhelper.Controller;

import org.example.amortizationhelper.Entity.HorseResult;
import org.example.amortizationhelper.Tools.TravTools;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/trav/analysis")
public class TravAnalysisApiController {

    private static final String ANALYSIS_TYPE_REGULAR = "regular";
    private static final int DEFAULT_TOP_N = 3;
    private static final int MAX_TOP_N = 20;

    private final TravTools travTools;

    public TravAnalysisApiController(TravTools travTools) {
        this.travTools = travTools;
    }

    @GetMapping(value = "/top-by-day", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TopByDayResponse> topByDay(
            @RequestParam("date") String date,
            @RequestParam("track") String track,
            @RequestParam(name = "form", defaultValue = "vinnare") String form,
            @RequestParam(name = "topN", defaultValue = "3") Integer topN
    ) {
        int normalizedTopN = normalizeTopN(topN);

        List<RaceTopResponse> races = travTools
                .topByDayTrackForm(date, track, form, normalizedTopN)
                .stream()
                .map(loppTopN -> new RaceTopResponse(
                        loppTopN.lap,
                        toHorseResponses(loppTopN.top)
                ))
                .toList();

        String banKod = races.stream()
                .flatMap(race -> race.top().stream())
                .map(HorseTopResponse::banKod)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        String spelFormUsed = races.stream()
                .flatMap(race -> race.top().stream())
                .map(HorseTopResponse::spelForm)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(form);

        return ResponseEntity.ok(new TopByDayResponse(
                1,
                ANALYSIS_TYPE_REGULAR,
                date,
                track,
                banKod,
                form,
                spelFormUsed,
                normalizedTopN,
                !races.isEmpty(),
                races
        ));
    }

    private static int normalizeTopN(Integer topN) {
        if (topN == null || topN <= 0) return DEFAULT_TOP_N;
        return Math.min(topN, MAX_TOP_N);
    }

    private static List<HorseTopResponse> toHorseResponses(List<HorseResult> horses) {
        if (horses == null) return List.of();

        return IntStream.range(0, horses.size())
                .mapToObj(index -> toHorseResponse(index + 1, horses.get(index)))
                .toList();
    }

    private static HorseTopResponse toHorseResponse(int rank, HorseResult horse) {
        return new HorseTopResponse(
                rank,
                horse.getId(),
                horse.getBanKod(),
                horse.getLap(),
                horse.getNumberOfHorse(),
                horse.getNameOfHorse(),
                parsePercent(horse.getProcentAnalys()),
                horse.getSpelForm(),
                horse.getStarter()
        );
    }

    private static Integer parsePercent(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record TopByDayResponse(
            int formatVersion,
            String analysisType,
            String date,
            String track,
            String banKod,
            String requestedForm,
            String spelFormUsed,
            int topN,
            boolean found,
            List<RaceTopResponse> races
    ) {
    }

    public record RaceTopResponse(
            String lopp,
            List<HorseTopResponse> top
    ) {
    }

    public record HorseTopResponse(
            int rank,
            Long id,
            String banKod,
            String lopp,
            Integer number,
            String name,
            Integer analysis,
            String spelForm,
            String starter
    ) {
    }
}
