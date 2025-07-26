package org.example.amortizationhelper.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "roi")
public class Roi {
    @Id
    private Long id;

    @Column(name = "rankid")
    private Long rankId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rankid", insertable = false, updatable = false)
    private HorseResult rank;

    @Column(name = "roitotalt")
    private BigDecimal roiTotalt;

    @Column(name = "roivinnare")
    private BigDecimal roiVinnare;

    @Column(name = "roiplats")
    private BigDecimal roiPlats;

    @Column(name = "roitrio")
    private BigDecimal roiTrio;

    @Column(name = "resultat")
    private Integer resultat;
}
