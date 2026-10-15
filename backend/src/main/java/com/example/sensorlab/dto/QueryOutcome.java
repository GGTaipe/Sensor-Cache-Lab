package com.example.sensorlab.dto;

import java.util.List;

/**
 * Resultado de una consulta medida.
 *
 * @param source DATABASE, CACHE_MISS o CACHE_HIT
 */
public record QueryOutcome<T>(String scenario, String source, int totalRecords,
                              double serverMs, List<T> sample) {
}
