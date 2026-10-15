package com.example.sensorlab.service;

import com.example.sensorlab.config.CacheConfig;
import com.example.sensorlab.dto.CacheStatsView;
import com.example.sensorlab.dto.MonthlyStat;
import com.example.sensorlab.dto.ReadingDto;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Decorador con caché sobre {@link ReadingDataService}.
 *
 * Al ser un bean distinto del que contiene la lógica de BD, el proxy de Spring siempre
 * intercepta la llamada (no hay problema de auto-invocación) y el código de acceso a datos
 * queda libre de anotaciones de caché.
 */
@Service
public class CachedReadingService {

    private final ReadingDataService delegate;
    private final CacheManager cacheManager;

    public CachedReadingService(ReadingDataService delegate, CacheManager cacheManager) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
    }

    // sync = true: si dos peticiones llegan a la vez con la caché vacía, solo una consulta la BD.
    @Cacheable(cacheNames = CacheConfig.READINGS, key = "#station", sync = true)
    public List<ReadingDto> readingsOf(String station) {
        return delegate.readingsOf(station);
    }

    @Cacheable(cacheNames = CacheConfig.REPORT, key = "'global'", sync = true)
    public List<MonthlyStat> monthlyReport() {
        return delegate.monthlyReport();
    }

    public boolean isReadingsCached(String station) {
        return contains(CacheConfig.READINGS, station);
    }

    public boolean isReportCached() {
        return contains(CacheConfig.REPORT, "global");
    }

    /**
     * Consulta la existencia de una clave SIN contar un acierto/fallo en las estadísticas
     * (cache.get() sí los contaría y duplicaría los contadores de /api/cache/stats).
     */
    private boolean contains(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache instanceof CaffeineCache cc && cc.getNativeCache().asMap().containsKey(key);
    }

    public void evictAll() {
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    public List<CacheStatsView> stats() {
        return cacheManager.getCacheNames().stream().sorted().map(name -> {
            CaffeineCache cc = (CaffeineCache) cacheManager.getCache(name);
            CacheStats s = cc.getNativeCache().stats();
            return new CacheStatsView(name, s.hitCount(), s.missCount(),
                    Math.round(s.hitRate() * 1000.0) / 1000.0,
                    s.evictionCount(), cc.getNativeCache().estimatedSize());
        }).toList();
    }
}
