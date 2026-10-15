package com.example.sensorlab.dto;

import java.util.List;

/** Comparación BD vs caché: todas las mediciones y el resumen estadístico. */
public record DuelResult(String scenario, String target, int rounds, int records,
                         List<Attempt> database, List<Attempt> cache,
                         double dbMedianMs, double dbAvgMs,
                         double hitMedianMs, double hitAvgMs,
                         double missMs, double speedup) {
}
