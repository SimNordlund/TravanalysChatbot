package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.KopAndel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KopAndelRepo extends JpaRepository<KopAndel, Long> {
    List<KopAndel> findAllByOrderByIdAsc();
}

