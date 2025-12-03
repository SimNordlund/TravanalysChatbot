package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.HorseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

//RANK REPO
@Repository
public interface HorseResultRepo extends JpaRepository<HorseResult, Long> {
    List<HorseResult> findByStartDateAndBanKod(Integer startDate, String banKod);

    List<HorseResult> findByNameOfHorseContainingIgnoreCase(String name);

    List<HorseResult> findByStartDateAndBanKodAndLap(Integer startDate, String banKod, String lap);

    List<HorseResult> findByNameOfHorseContainingIgnoreCaseOrderByStartDateDesc(String name);

    List<HorseResult> findByStartDateAndBanKodAndLapAndTips(Integer startDate, String banKod, String lap, Integer tips);

    List<HorseResult> findByStartDateAndBanKodAndLapAndSpelFormIgnoreCaseAndStarter(
            Integer startDate, String banKod, String lap, String spelForm, String starter);

    List<HorseResult> findByStartDateAndBanKodAndLapAndSpelFormIgnoreCase(
            Integer startDate, String banKod, String lap, String spelForm);

    List<HorseResult> findByStartDateAndBanKodAndSpelFormIgnoreCase(Integer startDate, String banKod, String spelForm); //Changed!

    @Query("select distinct hr.banKod from HorseResult hr where hr.startDate = :startDate order by hr.banKod")
    List<String> distinctBanKodByDate(@Param("startDate") Integer startDate);

    @Query("select distinct lower(hr.spelForm) from HorseResult hr where hr.startDate = :startDate and hr.banKod = :banKod order by lower(hr.spelForm)")
    List<String> distinctSpelFormByDateAndBanKod(@Param("startDate") Integer startDate, @Param("banKod") String banKod);

    @Query("select distinct hr.lap from HorseResult hr where hr.startDate = :startDate and hr.banKod = :banKod and (:spelForm is null or lower(hr.spelForm) = lower(:spelForm)) order by hr.lap")
    List<String> distinctLapByDateBanKodAndForm(@Param("startDate") Integer startDate, @Param("banKod") String banKod, @Param("spelForm") String spelForm);

    @Query("select distinct hr.starter from HorseResult hr where hr.startDate = :startDate and hr.banKod = :banKod and hr.lap = :lap and (:spelForm is null or lower(hr.spelForm) = lower(:spelForm)) order by hr.starter")
    List<String> distinctStartersByDateBanKodFormLap(@Param("startDate") Integer startDate, @Param("banKod") String banKod, @Param("spelForm") String spelForm, @Param("lap") String lap);

    @Query("select distinct hr.startDate from HorseResult hr where hr.banKod = :banKod order by hr.startDate desc")
    List<Integer> distinctDatesByBanKod(@Param("banKod") String banKod);

    @Query("select distinct hr.startDate from HorseResult hr order by hr.startDate desc")
    List<Integer> distinctDatesAll();

    @Query("select hr from HorseResult hr where hr.startDate = :startDate and hr.banKod = :banKod and hr.lap = :lap and (:spelForm is null or lower(hr.spelForm) = lower(:spelForm))")
    List<HorseResult> findField(@Param("startDate") Integer startDate, @Param("banKod") String banKod, @Param("lap") String lap, @Param("spelForm") String spelForm);
}
