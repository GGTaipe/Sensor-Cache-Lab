package com.example.sensorlab;

import com.example.sensorlab.dto.DuelResult;
import com.example.sensorlab.dto.MonthlyStat;
import com.example.sensorlab.dto.QueryOutcome;
import com.example.sensorlab.dto.ReadingDto;
import com.example.sensorlab.service.CachedReadingService;
import com.example.sensorlab.service.LabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el comportamiento de la caché con un conjunto de datos reducido (200 filas por estación). */
@SpringBootTest(properties = "lab.seed.readings-per-station=200")
class CacheBehaviourTest {

    @Autowired
    LabService lab;

    @Autowired
    CachedReadingService cached;

    @BeforeEach
    void emptyCaches() {
        cached.evictAll();
    }

    @Test
    void firstCachedReadIsMissAndSecondIsHit() {
        QueryOutcome<ReadingDto> first = lab.readings("QUITO", true, 5);
        QueryOutcome<ReadingDto> second = lab.readings("QUITO", true, 5);

        assertEquals("CACHE_MISS", first.source());
        assertEquals("CACHE_HIT", second.source());
        assertEquals(200, first.totalRecords());
        assertEquals(first.totalRecords(), second.totalRecords());
        assertEquals(5, second.sample().size());
    }

    @Test
    void databaseModeNeverTouchesTheCache() {
        lab.readings("LOJA", false, 0);
        assertFalse(cached.isReadingsCached("LOJA"));
    }

    @Test
    void reportAggregatesEveryReading() {
        QueryOutcome<MonthlyStat> out = lab.report(false, 100);
        long samples = out.sample().stream().mapToLong(MonthlyStat::samples).sum();
        assertTrue(out.totalRecords() > 0);
        assertEquals(12L * 200L, samples); // 12 estaciones x 200 lecturas
    }

    @Test
    void duelReturnsOneMissFollowedByHits() {
        DuelResult duel = lab.duel(LabService.Scenario.READINGS, "CUENCA", 4);

        assertEquals(4, duel.database().size());
        assertEquals(4, duel.cache().size());
        assertEquals("CACHE_MISS", duel.cache().get(0).source());
        assertTrue(duel.cache().stream().skip(1).allMatch(a -> a.source().equals("CACHE_HIT")));
    }
}
