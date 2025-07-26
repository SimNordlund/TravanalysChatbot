package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.HorseResult;
import org.example.amortizationhelper.repo.HorseResultRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

//Tools för rank tabell
@Component
@AllArgsConstructor
public class TravTools {

    private final HorseResultRepo horseResultRepo;

    @Tool(description = "Hämta värden om hästar baserat på ett id.")
    public HorseResult getHorseValues(Long id) {
        return horseResultRepo.findById(id).orElse(null);
    }

    @Tool(description = "Lista resultat för ett datum (YYYYMMDD eller YYYY-MM-DD) och bankod.")
    public List<HorseResult> listByDateAndTrackFlexible(String date, String banKod) {
        // Konvertera "2025-07-17" -> "20250717"
        String cleaned = date.replaceAll("-", "");
        Integer start;
        try {
            start = Integer.valueOf(cleaned);
        } catch (NumberFormatException e) {
            return List.of();
        }
        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKod(start, banKod);
        System.out.println("Tool listByDateAndTrackFlexible hittade " + results.size() + " rader");
        return results;
    }

    @Tool(
            name = "results_by_date_track_lap",
            description = "Lista Resultat (Analys/Prestation/Motstånd/Tid) för datum, bankod och lopp."
    )
    public List<HorseResult> listResultsByDateAndTrackAndLap(String date, String banKod, String lap) {

        String cleaned = date.replaceAll("-", "");
        Integer startDate;
        try {
            startDate = Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return List.of();
        }
        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKodAndLap(startDate, banKod, lap);
        System.out.println("Tool listByDateAndTrackAndLap hittade " + results.size() + " rader");
        return results;
    }

    @Tool(description = "Sök fram en häst och dess värden baserat på namnet på hästen")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        return horseResultRepo.findByNameOfHorseContainingIgnoreCase(nameFragment);
    }

    @Tool(description = "Hämta topp N hästar (Analys) för datum, bankod och lopp.")
    public List<HorseResult> topHorses(String date, String banKod, String lap, Integer limit) {
        if (limit == null || limit <= 0) limit = 3;
        List<HorseResult> all = listResultsByDateAndTrackAndLap(date, banKod, lap);
        return all.stream()
                .sorted((a,b) -> parse(b.getProcentAnalys()) - parse(a.getProcentAnalys()))
                .limit(limit)
                .toList();
    }
    private int parse(String val) {
        try { return Integer.parseInt(val); } catch (Exception e) { return -1; }
    }

    @Tool(description = "Visa en hästs senaste starter sorterade efter datum (senaste först).")
    public List<HorseResult> horseHistory(String nameFragment, Integer limit) {
        if (limit == null || limit <= 0) limit = 5;
        return horseResultRepo
                .findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(nameFragment)
                .stream()
                .limit(limit)
                .toList();
    }

}
