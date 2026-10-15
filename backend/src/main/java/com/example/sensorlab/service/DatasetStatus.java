package com.example.sensorlab.service;

import com.example.sensorlab.domain.StationCatalog;
import com.example.sensorlab.dto.DatasetInfo;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Estado de la carga inicial de datos, consultable desde el front. */
@Component
public class DatasetStatus {

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong seedMs = new AtomicLong();

    public void markReady(long totalReadings, long elapsedMs) {
        total.set(totalReadings);
        seedMs.set(elapsedMs);
        ready.set(true);
    }

    public boolean isReady() {
        return ready.get();
    }

    public DatasetInfo snapshot() {
        return new DatasetInfo(ready.get(), total.get(), seedMs.get(), StationCatalog.codes());
    }
}
