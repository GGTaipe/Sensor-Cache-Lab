package com.example.sensorlab.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Dos cachés con políticas distintas, construidas a mano para poder activar recordStats()
 * y así exponer aciertos/fallos reales en /api/cache/stats.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String READINGS = "stationReadings";
    public static final String REPORT = "monthlyReport";

    @Bean
    public CacheManager cacheManager() {
        // Listas grandes: pocas entradas y vida corta para acotar la memoria.
        CaffeineCache readings = new CaffeineCache(READINGS, Caffeine.newBuilder()
                .maximumSize(6)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build());

        // Reporte agregado: una sola entrada, cara de calcular y barata de guardar.
        CaffeineCache report = new CaffeineCache(REPORT, Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats()
                .build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(readings, report));
        return manager;
    }
}
