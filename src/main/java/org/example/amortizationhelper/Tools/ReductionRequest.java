package org.example.amortizationhelper.Tools;

import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.List;

/** Samma rankvillkor som på reduceringssidan; hästlistornas ordning är rankordningen. */
public record ReductionRequest(
        @ToolParam(description = "Trio, V3, V4, V5, V64, V65, GS75, V85 eller V86") String spelform,
        @ToolParam(description = "Användarens tävlingsdatum, YYYY-MM-DD eller YYYYMMDD. Gissa inte datum.") String datum,
        @ToolParam(description = "En enda bana, exempelvis Solvalla, Åby eller banans kod. Flera banor stöds inte.") String bana,
        @ToolParam(description = "Faktiskt startlopp för första avdelningen; för Trio det enda loppet. Efterföljande avdelningar måste vara följande lopp på samma bana.") Integer startlopp,
        @ToolParam(description = "Radpris i kronor som användaren angett eller som verifierats för omgången. Gissa inte. För Trio skrivs insatsen i XML; för övriga spel används priset endast i kostnadsberäkningen.") BigDecimal radpris,
        @ToolParam(description = "Startnummer i rankordning per avdelning; för Trio tre listor för etta, tvåa, trea. Ett nummer innebär spik. Behåll användarens ordning.") List<List<Integer>> avdelningar,
        @ToolParam(required = false, description = "Rankvillkor. Utelämnat innebär sidans standard: minRank 0, maxRank 13, inget krav på två förstarankade och inga exkluderade rader.") Filter filter) {

    public record Filter(
            @ToolParam(required = false, description = "Minsta SUMMA av rankpositioner på en rad, standard 0") Integer minRank,
            @ToolParam(required = false, description = "Högsta SUMMA av rankpositioner på en rad, standard 13. För ingen rankreducering: minst summan av listornas längder.") Integer maxRank,
            @ToolParam(required = false, description = "Kräv minst två rank-1-val på varje rad, standard false") Boolean requireAtLeastTwoFirstRanked,
            @ToolParam(required = false, description = "Exakta textrader med 16 siffror att utesluta, standard tom lista. Detta är inte hästlistor.") List<String> excludedTextRows) { }
}
