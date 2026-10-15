package com.example.sensorlab.dto;

/** Agregado mensual por estación. */
public record MonthlyStat(String stationCode, int year, int month,
                          double avgTemp, double minTemp, double maxTemp,
                          double avgHumidity, long samples) {
}
