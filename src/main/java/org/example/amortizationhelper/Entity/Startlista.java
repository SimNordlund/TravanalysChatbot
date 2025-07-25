package org.example.amortizationhelper.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name ="startlista")
public class Startlista {
    @Id
    private Long id;

    @Column(name = "startdatum")
    private Integer startDate;

    @Column(name = "bankod")
    private String banKod;

    @Column(name = "lopp")
    private Integer lap;

    @Column(name = "nr")
    private Integer numberOfHorse;

    @Column(name = "namn")
    private String nameOfHorse;

    @Column(name = "spar")
    private Integer spar;

    @Column(name = "distans")
    private Integer distans;

    @Column(name = "kusk")
    private String kusk;
}
