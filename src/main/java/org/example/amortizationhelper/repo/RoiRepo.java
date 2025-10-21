package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.Roi;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoiRepo extends JpaRepository<Roi, Long> {

    @EntityGraph(attributePaths = "rank")
    List<Roi> findByRank_StartDateAndRank_BanKod(Integer startDate, String banKod);

    @Query("""
           select r from Roi r
           join fetch r.rank hr
           where hr.startDate = :startDate
             and hr.banKod = :banKod
             and hr.lap = :lap
           """)
    List<Roi> fetchRoiByDateTrackLap(@Param("startDate") Integer startDate,
                                     @Param("banKod") String banKod,
                                     @Param("lap") String lap);


    @Query("""
           select distinct hr.startDate from Roi r
           join r.rank hr
           order by hr.startDate desc
           """)
    List<Integer> distinctDatesAll();

    @Query("""
           select distinct hr.banKod from Roi r
           join r.rank hr
           where hr.startDate = :startDate
           order by hr.banKod
           """)
    List<String> distinctBanKodByDate(@Param("startDate") Integer startDate);

    @Query("""
           select distinct hr.lap from Roi r
           join r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod
           order by hr.lap
           """)
    List<String> distinctLapsByDateAndBanKod(@Param("startDate") Integer startDate,
                                             @Param("banKod") String banKod);

    @Query("""
           select r from Roi r
           join fetch r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod
           """)
    List<Roi> fetchRoiByDateTrack(@Param("startDate") Integer startDate,
                                  @Param("banKod") String banKod);

    @Query("""
           select r from Roi r
           join r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod and hr.lap = :lap
             and lower(hr.nameOfHorse) like lower(concat('%', :name, '%'))
           """)
    List<Roi> fetchRoiByDTLAndHorseLike(@Param("startDate") Integer startDate,
                                        @Param("banKod") String banKod,
                                        @Param("lap") String lap,
                                        @Param("name") String name);

    @Query("""
           select r from Roi r
           join r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod and hr.lap = :lap
             and hr.numberOfHorse = :nr
           """)
    Optional<Roi> findByDTLAndNumber(@Param("startDate") Integer startDate,
                                     @Param("banKod") String banKod,
                                     @Param("lap") String lap,
                                     @Param("nr") Integer nr);

    @Query("""
           select coalesce(sum(r.roiTotalt), 0)
           from Roi r join r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod
           """)
    BigDecimal sumTotaltByDateTrack(@Param("startDate") Integer startDate,
                                    @Param("banKod") String banKod);

    @Query("""
           select coalesce(sum(r.roiTotalt), 0)
           from Roi r join r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod and hr.lap = :lap
           """)
    BigDecimal sumTotaltByDateTrackLap(@Param("startDate") Integer startDate,
                                       @Param("banKod") String banKod,
                                       @Param("lap") String lap);

    @Query("""
           select coalesce(sum(r.roiVinnare), 0),
                  coalesce(sum(r.roiPlats), 0),
                  coalesce(sum(r.roiTrio), 0)
           from Roi r join r.rank hr
           where hr.startDate = :startDate and hr.banKod = :banKod and (:lap is null or hr.lap = :lap)
           """)
    Object[] sumsByDateTrackAndOptionalLap(@Param("startDate") Integer startDate,
                                           @Param("banKod") String banKod,
                                           @Param("lap") String lap);
}
