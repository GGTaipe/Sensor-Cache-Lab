package com.example.sensorlab.repository;

import com.example.sensorlab.domain.Reading;
import com.example.sensorlab.dto.ReadingDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    /** Serie completa de una estación (~30.000 filas), directo a DTO sin entidades gestionadas. */
    @Query("""
            select new com.example.sensorlab.dto.ReadingDto(
                r.id, r.stationCode, r.recordedAt, r.temperature, r.humidity, r.pressure)
            from Reading r
            where r.stationCode = :station
            order by r.recordedAt
            """)
    List<ReadingDto> findSeries(@Param("station") String station);

    /**
     * Agregación pesada: recorre TODA la tabla y devuelve pocas filas.
     * Columnas: estación, año, mes, temp media, mín, máx, humedad media, nº de muestras.
     */
    @Query("""
            select r.stationCode, extract(year from r.recordedAt), extract(month from r.recordedAt),
                   avg(r.temperature), min(r.temperature), max(r.temperature),
                   avg(r.humidity), count(r)
            from Reading r
            group by r.stationCode, extract(year from r.recordedAt), extract(month from r.recordedAt)
            order by r.stationCode, extract(year from r.recordedAt), extract(month from r.recordedAt)
            """)
    List<Object[]> monthlyAggregates();
}
