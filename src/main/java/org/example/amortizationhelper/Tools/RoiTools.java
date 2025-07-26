package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Roi;
import org.example.amortizationhelper.repo.RoiRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
@AllArgsConstructor
public class RoiTools {

    private final RoiRepo roiRepo;

    @Tool(name = "roi_total_by_date_track",
            description = "Summera ROI Totalt för alla hästar på ett datum (YYYYMMDD/ÅÅÅÅ-MM-DD) och bankod.")
    public BigDecimal roiTotalByDateAndTrack(String date, String banKod) {
        Integer d = toIntDate(date);
        if (d == null) return BigDecimal.ZERO;
        String normBan = norm(banKod);
        return roiRepo.findByRank_StartDateAndRank_BanKod(d, normBan)
                .stream()
                .map(Roi::getRoiTotalt)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Tool(name = "roi_by_date_track_lap",
            description = "Hämta ROI (totalt/vinnare/plats/trio) för datum, bankod och lopp. Returnerar även hästnamn och startnummer.")

    public List<RoiDto> roiByDateTrackLap(String date, String banKod, String lap) {
        Integer d = toIntDate(date);
        if (d == null) return List.of();
        String normBan = norm(banKod);
        return roiRepo.fetchRoiByDateTrackLap(d, normBan, lap).stream()
                .map(this::toDto)
                .toList();
    }

    private RoiDto toDto(Roi r) {
        var hr = r.getRank();
        return new RoiDto(
                hr != null ? hr.getNameOfHorse() : null,
                hr != null ? hr.getNumberOfHorse() : null,
                hr != null ? hr.getBanKod() : null,
                hr != null ? hr.getLap() : null,
                r.getRoiTotalt(),
                r.getRoiVinnare(),
                r.getRoiPlats(),
                r.getRoiTrio()
        );
    }

    private Integer toIntDate(String date) {
        try {
            return Integer.valueOf(date.replaceAll("-", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String norm(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }

    public record RoiDto(
            String horseName,
            Integer numberOfHorse,
            String banKod,
            String lap,
            BigDecimal roiTotalt,
            BigDecimal roiVinnare,
            BigDecimal roiPlats,
            BigDecimal roiTrio
    ) {
    }
}
