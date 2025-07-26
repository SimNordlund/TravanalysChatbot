package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.Roi;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    List<Roi> fetchRoiByDateTrackLap(Integer startDate, String banKod, String lap);

}
