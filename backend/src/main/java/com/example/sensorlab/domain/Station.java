package com.example.sensorlab.domain;

/** Estación de medición: código, temperatura media base (°C) y altitud (m). */
public record Station(String code, double baseTemp, int altitude) {
}
