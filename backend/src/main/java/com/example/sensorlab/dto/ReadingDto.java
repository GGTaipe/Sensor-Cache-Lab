package com.example.sensorlab.dto;

import java.time.LocalDateTime;

/** Proyección inmutable de una lectura (segura para guardar en caché). */
public record ReadingDto(Long id, String stationCode, LocalDateTime recordedAt,
                         Double temperature, Double humidity, Double pressure) {
}
