package org.example.amortizationhelper.Entity;

public record AmorteringsUnderlag(
    String mortgageObject,
    String amorteringsgrundandeVarde, String amorteringsgrundandeSkuld,
    String datumForAmorteringsgrundandeVarde) { }
