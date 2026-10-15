package com.example.sensorlab.service;

import com.example.sensorlab.dto.MonthlyStat;
import com.example.sensorlab.dto.ReadingDto;
import com.example.sensorlab.repository.ReadingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Acceso directo a la base de datos. Sin caché: es la "línea base" de la comparación. */
@Service
public class ReadingDataService {

    private final ReadingRepository repository;

    public ReadingDataService(ReadingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ReadingDto> readingsOf(String station) {
        return repository.findSeries(station);
    }

    @Transactional(readOnly = true)
    public List<MonthlyStat> monthlyReport() {
        List<Object[]> rows = repository.monthlyAggregates();
        List<MonthlyStat> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new MonthlyStat(
                    (String) r[0],
                    ((Number) r[1]).intValue(),
                    ((Number) r[2]).intValue(),
                    round2(((Number) r[3]).doubleValue()),
                    ((Number) r[4]).doubleValue(),
                    ((Number) r[5]).doubleValue(),
                    round2(((Number) r[6]).doubleValue()),
                    ((Number) r[7]).longValue()));
        }
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
