package org.example.amortizationhelper.mcp;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class TrackWeatherMapper {

    private TrackWeatherMapper() {
    }

    private static final Map<String, String> TRACK_TO_WEATHER_QUERY = Map.ofEntries(
            Map.entry("solvalla", "Stockholm, Sweden"),
            Map.entry("jagersro", "Malmo, Sweden"),
            Map.entry("aby", "Molndal, Sweden"),
            Map.entry("taby trav", "Taby, Sweden"),
            Map.entry("broparktrav", "Bro, Upplands-Bro, Sweden"),

            Map.entry("bjerke", "Oslo, Norway"),
            Map.entry("jarlsberg", "Tonsberg, Norway"),
            Map.entry("momarken", "Mysen, Norway"),
            Map.entry("forus", "Stavanger, Norway"),
            Map.entry("leangen", "Trondheim, Norway"),
            Map.entry("klosterskogen", "Skien, Norway"),
            Map.entry("drammen", "Drammen, Norway"),
            Map.entry("bergen", "Bergen, Norway"),
            Map.entry("harstad", "Harstad, Norway"),
            Map.entry("haugaland", "Haugesund, Norway"),
            Map.entry("bodo", "Bodo, Norway"),
            Map.entry("biri", "Biri, Norway"),
            Map.entry("sorlandet", "Kristiansand, Norway"),
            Map.entry("orkla", "Orkanger, Norway"),

            Map.entry("charlottenlund", "Charlottenlund, Copenhagen, Denmark"),
            Map.entry("odense", "Odense, Denmark"),
            Map.entry("skive", "Skive, Denmark"),
            Map.entry("alborg", "Aalborg, Denmark"),
            Map.entry("arhus", "Aarhus, Denmark"),
            Map.entry("bornholm", "Bornholm, Denmark"),
            Map.entry("nykobing", "Nykobing Falster, Denmark"),
            Map.entry("billund", "Billund, Denmark"),

            Map.entry("vermo", "Espoo, Finland"),
            Map.entry("abo", "Turku, Finland"),
            Map.entry("forssa", "Forssa, Finland"),
            Map.entry("harma", "Yliharma, Finland"),
            Map.entry("joensuu", "Joensuu, Finland"),
            Map.entry("jyvaskyla", "Jyvaskyla, Finland"),
            Map.entry("kaustinen", "Kaustinen, Finland"),
            Map.entry("kouvola", "Kouvola, Finland"),
            Map.entry("kuopio", "Kuopio, Finland"),
            Map.entry("lahti", "Lahti, Finland"),
            Map.entry("lappeenranta", "Lappeenranta, Finland"),
            Map.entry("loviisa", "Loviisa, Finland"),
            Map.entry("mariehamn", "Mariehamn, Aland, Finland"),
            Map.entry("mikkeli", "Mikkeli, Finland"),
            Map.entry("pori", "Pori, Finland"),
            Map.entry("rovaniemi", "Rovaniemi, Finland"),
            Map.entry("seinajoki", "Seinajoki, Finland"),
            Map.entry("tammerfors", "Tampere, Finland"),
            Map.entry("tornea", "Tornio, Finland"),
            Map.entry("uleaborg", "Oulu, Finland"),
            Map.entry("ylivieska", "Ylivieska, Finland")
    );

    private static final Map<String, String> TRACK_TO_COUNTRY = Map.ofEntries(
            Map.entry("arvika", "Sweden"),
            Map.entry("axevalla", "Sweden"),
            Map.entry("bergsaker", "Sweden"),
            Map.entry("boden", "Sweden"),
            Map.entry("bollnas", "Sweden"),
            Map.entry("dannero", "Sweden"),
            Map.entry("dala jarna", "Sweden"),
            Map.entry("eskilstuna", "Sweden"),
            Map.entry("jagersro", "Sweden"),
            Map.entry("farjestad", "Sweden"),
            Map.entry("gavle", "Sweden"),
            Map.entry("goteborg trav", "Sweden"),
            Map.entry("hagmyren", "Sweden"),
            Map.entry("halmstad", "Sweden"),
            Map.entry("hoting", "Sweden"),
            Map.entry("karlshamn", "Sweden"),
            Map.entry("kalmar", "Sweden"),
            Map.entry("lindesberg", "Sweden"),
            Map.entry("lycksele", "Sweden"),
            Map.entry("mantorp", "Sweden"),
            Map.entry("oviken", "Sweden"),
            Map.entry("romme", "Sweden"),
            Map.entry("rattvik", "Sweden"),
            Map.entry("solvalla", "Sweden"),
            Map.entry("skelleftea", "Sweden"),
            Map.entry("solanget", "Sweden"),
            Map.entry("tingsryd", "Sweden"),
            Map.entry("taby trav", "Sweden"),
            Map.entry("umaker", "Sweden"),
            Map.entry("vemdalen", "Sweden"),
            Map.entry("vaggeryd", "Sweden"),
            Map.entry("visby", "Sweden"),
            Map.entry("aby", "Sweden"),
            Map.entry("amal", "Sweden"),
            Map.entry("arjang", "Sweden"),
            Map.entry("orebro", "Sweden"),
            Map.entry("ostersund", "Sweden"),
            Map.entry("broparktrav", "Sweden"),

            Map.entry("bjerke", "Norway"),
            Map.entry("bodo", "Norway"),
            Map.entry("biri", "Norway"),
            Map.entry("bergen", "Norway"),
            Map.entry("drammen", "Norway"),
            Map.entry("forus", "Norway"),
            Map.entry("harstad", "Norway"),
            Map.entry("haugaland", "Norway"),
            Map.entry("jarlsberg", "Norway"),
            Map.entry("klosterskogen", "Norway"),
            Map.entry("leangen", "Norway"),
            Map.entry("momarken", "Norway"),
            Map.entry("sorlandet", "Norway"),
            Map.entry("orkla", "Norway"),

            Map.entry("arhus", "Denmark"),
            Map.entry("billund", "Denmark"),
            Map.entry("bornholm", "Denmark"),
            Map.entry("charlottenlund", "Denmark"),
            Map.entry("nykobing", "Denmark"),
            Map.entry("odense", "Denmark"),
            Map.entry("skive", "Denmark"),
            Map.entry("alborg", "Denmark"),

            Map.entry("abo", "Finland"),
            Map.entry("forssa", "Finland"),
            Map.entry("harma", "Finland"),
            Map.entry("joensuu", "Finland"),
            Map.entry("jyvaskyla", "Finland"),
            Map.entry("kaustinen", "Finland"),
            Map.entry("kouvola", "Finland"),
            Map.entry("kuopio", "Finland"),
            Map.entry("lahti", "Finland"),
            Map.entry("lappeenranta", "Finland"),
            Map.entry("loviisa", "Finland"),
            Map.entry("mariehamn", "Finland"),
            Map.entry("mikkeli", "Finland"),
            Map.entry("pori", "Finland"),
            Map.entry("rovaniemi", "Finland"),
            Map.entry("seinajoki", "Finland"),
            Map.entry("tammerfors", "Finland"),
            Map.entry("tornea", "Finland"),
            Map.entry("uleaborg", "Finland"),
            Map.entry("vermo", "Finland"),
            Map.entry("ylivieska", "Finland"),
            Map.entry("paikallisravit", "Finland")
    );

    public static String toWeatherQuery(String trackName) {
        if (trackName == null || trackName.isBlank()) {
            return "";
        }

        String normalized = normalize(trackName);

        String explicit = TRACK_TO_WEATHER_QUERY.get(normalized);
        if (explicit != null) {
            return explicit;
        }

        String country = TRACK_TO_COUNTRY.get(normalized);
        if (country != null) {
            return trackName + ", " + country;
        }

        return trackName;
    }

    private static String normalize(String value) {
        String s = value.toLowerCase(Locale.ROOT).trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^a-z0-9 ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
