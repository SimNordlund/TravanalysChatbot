package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Startlista;
import org.example.amortizationhelper.repo.StartlistaRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class StartlistaTools {

     private final StartlistaRepo startlistaRepo;

    @Tool(description = "Värden för en häst som tillhör en startlista")
    public Startlista getStartlistaValue(Long id) {
        return startlistaRepo.findById(id).orElse(null);
    }

    @Tool(
            name = "startlista_by_date_track_lap",
            description = "Lista Startlista (kusk/språ/distans) för datum (YYYYMMDD/YYYY-MM-DD), bankod och lopp."
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


}
