package org.example.amortizationhelper.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TrackWeatherTools {

    @Tool(
            name = "weather_location_for_track",
            description = "Normalizes Nordic trotting track names, city shortcuts, and common Swedish misspellings into a city/country query that weather geocoding can resolve. Use before weather geocoding when the user asks weather for a travbana, bana, or racetrack."
    )
    public TrackWeatherMapper.WeatherLocation weatherLocationForTrack(
            @ToolParam(description = "Track, city, or user phrase, for example Solvalla, Jägersro, Färejstad, Åby, Romme, or vädret på Färjestad idag")
            String trackOrPhrase) {
        return TrackWeatherMapper.resolve(trackOrPhrase);
    }
}
