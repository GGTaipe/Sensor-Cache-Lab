package com.example.sensorlab.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Una lectura horaria de una estación meteorológica. */
@Entity
@Table(name = "readings", indexes = {
        @Index(name = "idx_readings_station", columnList = "station_code"),
        @Index(name = "idx_readings_time", columnList = "recorded_at")
})
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", nullable = false, length = 20)
    private String stationCode;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private Double temperature;

    @Column(nullable = false)
    private Double humidity;

    @Column(nullable = false)
    private Double pressure;

    protected Reading() {
        // requerido por JPA
    }

    public Long getId() { return id; }
    public String getStationCode() { return stationCode; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public Double getTemperature() { return temperature; }
    public Double getHumidity() { return humidity; }
    public Double getPressure() { return pressure; }
}
