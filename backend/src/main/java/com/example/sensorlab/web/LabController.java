package com.example.sensorlab.web;

import com.example.sensorlab.domain.StationCatalog;
import com.example.sensorlab.dto.CacheStatsView;
import com.example.sensorlab.dto.DatasetInfo;
import com.example.sensorlab.dto.DuelResult;
import com.example.sensorlab.dto.MonthlyStat;
import com.example.sensorlab.dto.QueryOutcome;
import com.example.sensorlab.dto.ReadingDto;
import com.example.sensorlab.service.CachedReadingService;
import com.example.sensorlab.service.DatasetStatus;
import com.example.sensorlab.service.LabService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LabController {

    private final LabService lab;
    private final CachedReadingService cache;
    private final DatasetStatus status;

    public LabController(LabService lab, CachedReadingService cache, DatasetStatus status) {
        this.lab = lab;
        this.cache = cache;
        this.status = status;
    }

    @GetMapping("/status")
    public DatasetInfo status() {
        return status.snapshot();
    }

    /** Lee TODAS las lecturas de una estación. mode = db | cache. */
    @GetMapping("/readings/{station}")
    public ResponseEntity<QueryOutcome<ReadingDto>> readings(@PathVariable String station,
                                                             @RequestParam(defaultValue = "db") String mode,
                                                             @RequestParam(defaultValue = "20") int sample) {
        requireReady();
        String code = StationCatalog.require(station);
        QueryOutcome<ReadingDto> out = lab.readings(code, useCache(mode), clampSample(sample));
        return timed(out, out.serverMs(), out.source());
    }

    /** Agregado mensual de las 360.000 lecturas. mode = db | cache. */
    @GetMapping("/report/monthly")
    public ResponseEntity<QueryOutcome<MonthlyStat>> report(@RequestParam(defaultValue = "db") String mode,
                                                            @RequestParam(defaultValue = "20") int sample) {
        requireReady();
        QueryOutcome<MonthlyStat> out = lab.report(useCache(mode), clampSample(sample));
        return timed(out, out.serverMs(), out.source());
    }

    /** Ejecuta N consultas a la BD y N con caché y devuelve todos los tiempos. */
    @GetMapping("/lab/duel")
    public DuelResult duel(@RequestParam(defaultValue = "readings") String scenario,
                           @RequestParam(defaultValue = "QUITO") String station,
                           @RequestParam(defaultValue = "6") int rounds) {
        requireReady();
        LabService.Scenario sc = LabService.Scenario.parse(scenario);
        String code = sc == LabService.Scenario.READINGS ? StationCatalog.require(station) : "ALL";
        return lab.duel(sc, code, rounds);
    }

    @GetMapping("/cache/stats")
    public List<CacheStatsView> cacheStats() {
        return cache.stats();
    }

    @DeleteMapping("/cache")
    public Map<String, String> clearCache() {
        cache.evictAll();
        return Map.of("message", "Cachés vaciadas");
    }

    // ---------- utilidades ----------

    private void requireReady() {
        if (!status.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Los datos de prueba todavía se están generando");
        }
    }

    private static boolean useCache(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "cache" -> true;
            case "db" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode debe ser 'db' o 'cache'");
        };
    }

    private static int clampSample(int sample) {
        return Math.max(0, Math.min(sample, 200));
    }

    /**
     * Añade Server-Timing (estándar W3C, visible en la pestaña Network de DevTools)
     * y X-Data-Source. Ambas deben figurar en exposedHeaders del CORS para que el front las lea.
     */
    private static <T> ResponseEntity<T> timed(T body, double ms, String source) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Server-Timing", "app;dur=" + ms);
        headers.add("X-Data-Source", source);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
