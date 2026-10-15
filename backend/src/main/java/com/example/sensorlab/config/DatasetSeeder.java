package com.example.sensorlab.config;

import com.example.sensorlab.domain.Station;
import com.example.sensorlab.domain.StationCatalog;
import com.example.sensorlab.service.DatasetStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Genera lecturas horarias sintéticas con estacionalidad anual, ciclo diario y ruido.
 * Usa inserción por lotes con JdbcTemplate (mucho más rápida que repository.save()).
 */
@Component
public class DatasetSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatasetSeeder.class);
    private static final int BATCH = 5_000;
    private static final LocalDateTime START = LocalDateTime.of(2022, 1, 1, 0, 0);
    private static final String INSERT =
            "insert into readings (station_code, recorded_at, temperature, humidity, pressure) values (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final DatasetStatus status;
    private final int perStation;

    public DatasetSeeder(JdbcTemplate jdbc, DatasetStatus status,
                         @Value("${lab.seed.readings-per-station}") int perStation) {
        this.jdbc = jdbc;
        this.status = status;
        this.perStation = perStation;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        long t0 = System.nanoTime();
        log.info("Generando {} lecturas ({} estaciones x {})...",
                (long) perStation * StationCatalog.ALL.size(), StationCatalog.ALL.size(), perStation);

        Random rnd = new Random(7);
        List<Object[]> batch = new ArrayList<>(BATCH);
        long inserted = 0;

        for (Station st : StationCatalog.ALL) {
            double basePressure = 1013.25 * Math.exp(-st.altitude() / 8434.0);
            for (int i = 0; i < perStation; i++) {
                LocalDateTime when = START.plusHours(i);
                double yearly = 3.0 * Math.sin(2 * Math.PI * when.getDayOfYear() / 365.0);
                double daily = 4.0 * Math.sin(2 * Math.PI * (when.getHour() - 9) / 24.0);
                double temp = st.baseTemp() + yearly + daily + rnd.nextGaussian() * 0.8;
                double hum = clamp(80 - 1.5 * daily + rnd.nextGaussian() * 6, 20, 100);
                double pres = basePressure + rnd.nextGaussian() * 1.5;

                batch.add(new Object[]{st.code(), Timestamp.valueOf(when), round2(temp), round2(hum), round2(pres)});
                if (batch.size() == BATCH) {
                    jdbc.batchUpdate(INSERT, batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(INSERT, batch);
            inserted += batch.size();
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        status.markReady(inserted, ms);
        log.info("Listo: {} lecturas insertadas en {} ms.", inserted, ms);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
