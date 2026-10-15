package com.example.sensorlab.service;

import com.example.sensorlab.dto.Attempt;
import com.example.sensorlab.dto.DuelResult;
import com.example.sensorlab.dto.MonthlyStat;
import com.example.sensorlab.dto.QueryOutcome;
import com.example.sensorlab.dto.ReadingDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Orquesta las consultas y las mide con System.nanoTime(). */
@Service
public class LabService {

    public enum Scenario {
        READINGS, REPORT;

        public static Scenario parse(String raw) {
            return switch (raw == null ? "" : raw.toLowerCase()) {
                case "readings" -> READINGS;
                case "report" -> REPORT;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "scenario debe ser 'readings' o 'report'");
            };
        }
    }

    private final ReadingDataService db;
    private final CachedReadingService cached;

    public LabService(ReadingDataService db, CachedReadingService cached) {
        this.db = db;
        this.cached = cached;
    }

    // ---------- consultas individuales ----------

    public QueryOutcome<ReadingDto> readings(String station, boolean useCache, int sample) {
        if (!useCache) {
            return measure("READINGS", "DATABASE", sample, () -> db.readingsOf(station));
        }
        String source = cached.isReadingsCached(station) ? "CACHE_HIT" : "CACHE_MISS";
        return measure("READINGS", source, sample, () -> cached.readingsOf(station));
    }

    public QueryOutcome<MonthlyStat> report(boolean useCache, int sample) {
        if (!useCache) {
            return measure("REPORT", "DATABASE", sample, db::monthlyReport);
        }
        String source = cached.isReportCached() ? "CACHE_HIT" : "CACHE_MISS";
        return measure("REPORT", source, sample, cached::monthlyReport);
    }

    private QueryOutcome<?> run(Scenario scenario, String station, boolean useCache) {
        return scenario == Scenario.READINGS ? readings(station, useCache, 0) : report(useCache, 0);
    }

    private <T> QueryOutcome<T> measure(String scenario, String source, int sample, Supplier<List<T>> action) {
        long t0 = System.nanoTime();
        List<T> data = action.get();
        double ms = round3((System.nanoTime() - t0) / 1_000_000.0);
        return new QueryOutcome<>(scenario, source, data.size(), ms, data.stream().limit(sample).toList());
    }

    // ---------- duelo BD vs caché ----------

    public DuelResult duel(Scenario scenario, String station, int rounds) {
        int n = Math.max(3, Math.min(rounds, 15));

        List<Attempt> dbRuns = new ArrayList<>();
        int records = 0;
        for (int i = 1; i <= n; i++) {
            QueryOutcome<?> o = run(scenario, station, false);
            dbRuns.add(new Attempt(i, o.source(), o.serverMs()));
            records = o.totalRecords();
        }

        cached.evictAll(); // la primera pasada con caché debe ser un fallo (miss) garantizado
        List<Attempt> cacheRuns = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            QueryOutcome<?> o = run(scenario, station, true);
            cacheRuns.add(new Attempt(i, o.source(), o.serverMs()));
        }

        List<Double> dbTimes = dbRuns.stream().map(Attempt::ms).toList();
        List<Double> hitTimes = cacheRuns.stream().skip(1).map(Attempt::ms).toList();
        double dbMedian = median(dbTimes);
        double hitMedian = median(hitTimes);
        double speedup = dbMedian / Math.max(hitMedian, 0.001);

        return new DuelResult(scenario.name(), scenario == Scenario.READINGS ? station : "ALL",
                n, records, dbRuns, cacheRuns,
                round3(dbMedian), round3(average(dbTimes)),
                round3(hitMedian), round3(average(hitTimes)),
                cacheRuns.get(0).ms(), round3(speedup));
    }

    // ---------- estadística ----------

    private static double average(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int mid = s.size() / 2;
        return s.size() % 2 == 1 ? s.get(mid) : (s.get(mid - 1) + s.get(mid)) / 2.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
