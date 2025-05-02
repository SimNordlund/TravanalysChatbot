package org.example.amortizationhelper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowing frontend origins
        config.addAllowedOriginPattern("http://localhost:5173"); // Local dev
        config.addAllowedOriginPattern("https://travanalys.onrender.com"); // Old domain
        config.addAllowedOriginPattern("https://travanalys.se"); // New custom domain
        config.addAllowedOriginPattern("https://www.travanalys.se"); // New custom domain (www)

        // Specify allowed HTTP methods
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");

        // Specify allowed headers
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Content-Type");

        // Allow credentials (cookies, authentication headers)
        config.setAllowCredentials(true);

        // Apply the CORS configuration to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
