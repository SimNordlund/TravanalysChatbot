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
@Table(name = "rank")
//RANK TABELL
public class HorseResult {
    @Id
    private Long id;

    @Column(name = "startdatum")
    private Integer startDate;

    @Column(name = "bankod")
    private String banKod;

    @Column(name = "lopp")
    private String lap;

    @Column(name = "nr")
    private Integer numberOfHorse;

    @Column(name = "namn")
    private String nameOfHorse;

    @Column(name = "procenttid") ////fart
    private String procentFart;

    @Column(name = "procentprestation") //prestation
    private String procentPrestation;

    @Column(name = "procentmotstand") //motstånd
    private String procentMotstand;

    @Column(name = "procentklass") //klass
    private String  klassProcent;

    @Column(name = "procentanalys") //analys
    private String procentAnalys;

    @Column(name = "procentskrik") //skrik
    private String procentSkrik;

    @Column(name = "procentplacering") //placering
    private String procentPlacering;

    @Column(name ="procentform") //form
    private String procentForm;

    @Column(name = "spelform")
    private String spelForm;

    @Column(name = "starter")
    private String starter;

   @Column(name = "tips")
    private Integer tips;
}
