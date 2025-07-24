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

    @Tool(description = "Lista resultat för ett startdatum (YYYYMMDD eller YYYY-MM-DD) och bankod.") //Changed!
    public List<HorseResult> listByDateAndTrackFlexible(String date, String banKod) { //Changed!
        // Konvertera "2025-07-17" -> "20250717" //Changed!
        String cleaned = date.replaceAll("-", ""); //Changed!
        Integer start; //Changed!
        try { //Changed!
            start = Integer.valueOf(cleaned); //Changed!
        } catch (NumberFormatException e) { //Changed!
            return List.of(); //Changed!
        }
        List<HorseResult> results = horseResultRepo.findByStartDateAndBanKod(start, banKod); //Changed!
        System.out.println("Tool listByDateAndTrackFlexible hittade " + results.size() + " rader"); //Changed!
        return results; //Changed!
    }

    @Tool(description = "Sök hästar vars namn innehåller ett fragment.")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        return horseResultRepo.findByNameOfHorseContainingIgnoreCase(nameFragment);
    }

}
