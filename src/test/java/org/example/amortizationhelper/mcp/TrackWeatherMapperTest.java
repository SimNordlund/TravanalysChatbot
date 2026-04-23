package org.example.amortizationhelper.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackWeatherMapperTest {

    @Test
    void resolvesSolvallaToStockholm() {
        TrackWeatherMapper.WeatherLocation location = TrackWeatherMapper.resolve("solvalla");

        assertTrue(location.knownTrack());
        assertEquals("Solvalla", location.matchedTrack());
        assertEquals("Stockholm, Sweden", location.weatherQuery());
    }

    @Test
    void resolvesJagersroToMalmo() {
        TrackWeatherMapper.WeatherLocation location = TrackWeatherMapper.resolve("Vad är vädret på Jägersro idag?");

        assertTrue(location.knownTrack());
        assertEquals("Jägersro", location.matchedTrack());
        assertEquals("Malmö, Sweden", location.weatherQuery());
    }

    @Test
    void resolvesMisspelledFarejstadToKarlstad() {
        TrackWeatherMapper.WeatherLocation location = TrackWeatherMapper.resolve("Vädret på Färejstad idag");

        assertTrue(location.knownTrack());
        assertEquals("Färjestad", location.matchedTrack());
        assertEquals("Karlstad, Sweden", location.weatherQuery());
    }
}
