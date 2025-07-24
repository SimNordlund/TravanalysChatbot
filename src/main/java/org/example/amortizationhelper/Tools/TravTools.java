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

    @Tool(description = "Lista resultat för ett startdatum (YYYYMMDD) och bankod.")
    public List<HorseResult> listByDateAndTrack(Integer startDate, String banKod) {
        return horseResultRepo.findByStartDateAndBanKod(startDate, banKod);
    }

    @Tool(description = "Sök hästar vars namn innehåller ett fragment.")
    public List<HorseResult> searchByHorseName(String nameFragment) {
        return horseResultRepo.findByNameOfHorseContainingIgnoreCase(nameFragment);
    }

}
