package org.example.amortizationhelper.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class HorseResult {
    @Id
    private Long id;
    private String startDate;
    private String banKod;
    private String lap;
    private String numberOfHorse;
    private String nameOfHorse;
    private String procentTid;
    private String procentPrestation;
    private String procentMotstand;
    private String procentAnalys;


}
