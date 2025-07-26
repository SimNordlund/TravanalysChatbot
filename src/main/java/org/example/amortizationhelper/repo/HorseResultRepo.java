package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.HorseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseResultRepo extends JpaRepository<HorseResult, Long> {
    List<HorseResult> findByStartDateAndBanKod(Integer startDate, String banKod);

    List<HorseResult> findByNameOfHorseContainingIgnoreCase(String name);

    List<HorseResult> findByStartDateAndBanKodAndLap(Integer startDate, String banKod, String lap);

    List<HorseResult> findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(String name);

    //   List <HorseResult> findHorseResultByStartDateAndBanKod(Integer date, String banKod);
}
