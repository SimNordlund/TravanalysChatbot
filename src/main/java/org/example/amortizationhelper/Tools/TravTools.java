package org.example.amortizationhelper.Tools;

import org.example.amortizationhelper.Entity.HorseResult;
import org.example.amortizationhelper.repo.HorseResultRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TravTools {

    private final HorseResultRepo horseResultRepo;

    public TravTools(HorseResultRepo horseResultRepo) {
        this.horseResultRepo = horseResultRepo;
    }

    @Tool(description = "Hämta värden på id.")
    public HorseResult getHorseValues(Long id) {
        return horseResultRepo.findById(id).orElse(null);
    }

    @Tool(description = "Lista resultat för ett startdatum (YYYYMMDD eller YYYY-MM-DD) och bankod.")
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

    @Tool(description = "Sök fram en häst och dess värden baserat på namnet på hästen")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        return horseResultRepo.findByNameOfHorseContainingIgnoreCase(nameFragment);
    }

}
