package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.Startlista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StartlistaRepo extends JpaRepository<Startlista, Long> {

    List<Startlista> findByStartDateAndBanKodAndLap(Integer startDate, String banKod, Integer lap);

    List<Startlista> findByStartDateAndBanKod(Integer startDate, String banKod);

    List<Startlista> findByStartDateAndBanKodAndLapOrderByNumberOfHorseAsc(Integer startDate, String banKod, Integer lap);

    List<Startlista> findByStartDateAndBanKodOrderByLapAscNumberOfHorseAsc(Integer startDate, String banKod);

    Optional<Startlista> findFirstByStartDateAndBanKodAndLapAndNumberOfHorse(Integer startDate, String banKod, Integer lap, Integer numberOfHorse);

    List<Startlista> findByStartDateAndBanKodAndLapAndNameOfHorseContainingIgnoreCase(Integer startDate, String banKod, Integer lap, String nameOfHorse);

    @Query("select distinct s.startDate from Startlista s order by s.startDate desc")
    List<Integer> distinctDatesAll();

    @Query("select distinct s.banKod from Startlista s where s.startDate = :startDate order by s.banKod")
    List<String> distinctBanKodByDate(@Param("startDate") Integer startDate);

    @Query("select distinct s.lap from Startlista s where s.startDate = :startDate and s.banKod = :banKod order by s.lap")
    List<Integer> distinctLapsByDateAndBanKod(@Param("startDate") Integer startDate, @Param("banKod") String banKod);

    @Query("select distinct s.kusk from Startlista s where s.startDate = :startDate and s.banKod = :banKod and s.lap = :lap order by s.kusk")
    List<String> distinctKuskByDateTrackLap(@Param("startDate") Integer startDate, @Param("banKod") String banKod, @Param("lap") Integer lap);

    @Query("select distinct s.distans from Startlista s where s.startDate = :startDate and s.banKod = :banKod order by s.distans")
    List<Integer> distinctDistansByDateTrack(@Param("startDate") Integer startDate, @Param("banKod") String banKod);
}
