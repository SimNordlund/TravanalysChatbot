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
@Table(name ="rank")

//RANK TABELL
public class HorseResult {
    @Id
    private Long id;
    @Column(name = "startdatum")
    private Integer startDate;
    @Column(name ="bankod")
    private String banKod;
    @Column(name = "lopp")
    private String lap;
    @Column (name ="nr")
    private Integer numberOfHorse;
    @Column(name = "namn")
    private String nameOfHorse;
    @Column(name = "procenttid")
    private String procentTid;
    @Column(name = "procentprestation")
    private String procentPrestation;
    @Column(name = "procentmotstand")
    private String procentMotstand;
    @Column(name = "procentanalys")
    private String procentAnalys;
}
