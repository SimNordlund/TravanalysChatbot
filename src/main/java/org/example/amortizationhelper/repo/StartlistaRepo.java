package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.Startlista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StartlistaRepo extends JpaRepository<Startlista, Long> {

    List<Startlista> findByStartDateAndBanKodAndLap(Integer startDate, String banKod, Integer lap);

}
