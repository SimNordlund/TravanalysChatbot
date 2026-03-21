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
@Table(name = "appurl")
public class KopAndel {

    @Id
    private Long id;

    @Column(name = "url")
    private String url;

    @Column(name = "date")
    private String date;
}
