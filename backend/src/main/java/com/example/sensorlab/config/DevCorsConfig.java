package com.example.sensorlab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;
import java.util.List;

/**
 * CORS solo para el perfil "dev".
 *
 * Se registra como FILTRO de servlet (no como WebMvcConfigurer) con máxima prioridad:
 * el preflight OPTIONS se responde antes de llegar a Spring MVC y el mismo código
 * seguiría funcionando si mañana se añade Spring Security.
 *
 * Importante: exposedHeaders. Por defecto JavaScript solo puede leer unas pocas cabeceras
 * "seguras" de una respuesta cross-origin; las nuestras (Server-Timing, X-Data-Source)
 * hay que listarlas o fetch() las verá como null.
 */
@Configuration
@Profile("dev")
public class DevCorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${lab.cors.origins}") List<String> origins) {

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Server-Timing", "X-Data-Source"));
        cfg.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
