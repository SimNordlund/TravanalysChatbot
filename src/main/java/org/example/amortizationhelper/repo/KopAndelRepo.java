package org.example.amortizationhelper.repo;

import org.example.amortizationhelper.Entity.KopAndel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KopAndelRepo extends JpaRepository<KopAndel, Long> {
}

