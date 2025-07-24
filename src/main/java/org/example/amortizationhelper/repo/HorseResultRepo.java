package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.HorseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorseResultRepo extends JpaRepository <HorseResult, Long> {
    // List <HorseResult> 
}
