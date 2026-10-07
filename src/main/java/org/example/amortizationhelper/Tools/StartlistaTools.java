package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.Startlista;
import org.example.amortizationhelper.repo.StartlistaRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@AllArgsConstructor
public class StartlistaTools {

    private final StartlistaRepo startlistaRepo;

    @Tool(description = "Värden för en häst som tillhör en startlista")
    public Startlista getStartlistaValue(Long id) {
        return id == null ? null : startlistaRepo.findById(id).orElse(null);
    }

    @Tool(
            name = "startlista_by_date_track_lap",
            description = "Lista Startlista (kusk/spår/distans) för datum (YYYYMMDD/YYYY-MM-DD), bannamn/bankod och faktiskt loppnummer. Avdelning måste först kopplas till rätt lopp."
    )
    public List<Startlista> findStartListaByStartDateAndBanKodAndLap(String date, String banKod, String lap) {
        return startFieldSorted(date, banKod, lap);
    }


    @Tool(name = "start_dates_all", description = "Lista alla datum som finns i Startlista (senaste först).")
    public List<Integer> startDatesAll() {
        return startlistaRepo.distinctDatesAll();
    }

    @Tool(name = "start_tracks_by_date", description = "Lista bankoder på ett givet datum (från Startlista).")
    public List<String> startTracksByDate(String dateOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        if (d == null) return List.of();
        return startlistaRepo.distinctBanKodByDate(d);
    }

    @Tool(name = "start_laps_by_date_track", description = "Lista faktiska loppnummer för ett datum + bana.")
    public List<Integer> startLapsByDateTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return startlistaRepo.distinctLapsByDateAndBanKod(d, b);
    }

    @Tool(name = "start_kuskar_by_date_track_lap", description = "Lista kuskar för datum + bana + lopp.")
    public List<String> kuskarByDateTrackLap(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        Integer lap = parseLapFlexibleToInt(lapOrPhrase);
        if (d == null || b == null || lap == null) return List.of();
        return startlistaRepo.distinctKuskByDateTrackLap(d, b, lap);
    }

    @Tool(name = "start_distans_by_date_track", description = "Lista distanser för datum + bana.")
    public List<Integer> distansByDateTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return startlistaRepo.distinctDistansByDateTrack(d, b);
    }

    @Tool(name = "start_field_sorted", description = "Hämta hela startfältet (datum + bana + lopp) sorterat på startnummer (nr).")
    public List<Startlista> startFieldSorted(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        Integer lap = parseLapFlexibleToInt(lapOrPhrase);
        if (d == null || b == null || lap == null) return List.of();
        return startlistaRepo.findByStartDateAndBanKodAndLapOrderByNumberOfHorseAsc(d, b, lap);
    }

    @Tool(name = "start_field_for_track", description = "Hämta alla startlistor för datum + bana, sorterat (lopp↑, nr↑).")
    public List<Startlista> startFieldForTrack(String dateOrPhrase, String banKodOrTrack) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null) return List.of();
        return startlistaRepo.findByStartDateAndBanKodOrderByLapAscNumberOfHorseAsc(d, b);
    }

    @Tool(name = "start_search_horse", description = "Sök häst i startlistan för datum + bana (+ ev. lopp) via namnfragment.")
    public List<Startlista> searchHorseInStartlista(String dateOrPhrase, String banKodOrTrack, String nameFragment, String lapOrNull) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        if (d == null || b == null || nameFragment == null || nameFragment.isBlank()) return List.of();
        Integer lap = parseLapFlexibleToInt(lapOrNull);
        if (lap == null && lapOrNull != null && !lapOrNull.isBlank()) return List.of();
        if (lap == null) {
            // Filtrera per datum+bana och sök i minnet (enkelt alternativ)
            List<Startlista> base = startlistaRepo.findByStartDateAndBanKod(d, b);
            String nf = normalize(nameFragment);
            List<Startlista> out = new ArrayList<>();
            for (Startlista s : base) {
                if (normalize(s.getNameOfHorse()).contains(nf)) out.add(s);
            }
            out.sort(Comparator.comparing(Startlista::getLap, Comparator.nullsLast(Integer::compareTo)).thenComparing(Startlista::getNumberOfHorse, Comparator.nullsLast(Integer::compareTo)));
            return out;
        } else {
            return startlistaRepo.findByStartDateAndBanKodAndLapAndNameOfHorseContainingIgnoreCase(
                    d, b, lap, nameFragment);
        }
    }

    @Tool(name = "start_get_by_number", description = "Hämta en specifik start baserat på datum + bana + lopp + startnummer (nr).")
    public Startlista getStartByNumber(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase, Integer number) {
        Integer d = parseDateFlexible(dateOrPhrase);
        String b = resolveBanKodFlexible(banKodOrTrack);
        Integer lap = parseLapFlexibleToInt(lapOrPhrase);
        if (d == null || b == null || lap == null || number == null) return null;
        return startlistaRepo.findFirstByStartDateAndBanKodAndLapAndNumberOfHorse(d, b, lap, number).orElse(null);
    }

    @Tool(name = "start_grid", description = "Returnera ett lättviktigt rutnät för UI: nr, namn, kusk, spår, distans, lopp.")
    public List<StartRow> startGrid(String dateOrPhrase, String banKodOrTrack, String lapOrPhrase) {
        List<Startlista> rows = startFieldSorted(dateOrPhrase, banKodOrTrack, lapOrPhrase);
        List<StartRow> out = new ArrayList<>();
        for (Startlista s : rows) {
            out.add(new StartRow(
                    s.getNumberOfHorse(), s.getNameOfHorse(),
                    s.getKusk(), s.getSpar(), s.getDistans(), s.getLap()
            ));
        }
        return out;
    }

//helpers
    public static class StartRow {
        public Integer nr;
        public String namn;
        public String kusk;
        public Integer spar;
        public Integer distans;
        public Integer lopp;
        public StartRow(Integer nr, String namn, String kusk, Integer spar, Integer distans, Integer lopp) {
            this.nr = nr; this.namn = namn; this.kusk = kusk; this.spar = spar; this.distans = distans; this.lopp = lopp;
        }
    }

    private static String normalize(String value) {
        return TravQueryParser.normalize(value);
    }

    private static Integer parseDateFlexible(String value) {
        return TravQueryParser.date(value);
    }

    private static String resolveBanKodFlexible(String value) {
        return TravQueryParser.track(value);
    }

    private static Integer parseLapFlexibleToInt(String value) {
        String race = TravQueryParser.race(value);
        return race == null ? null : Integer.valueOf(race);
    }
}