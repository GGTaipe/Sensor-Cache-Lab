package com.example.sensorlab.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** Catálogo fijo de estaciones usado para generar y validar datos. */
public final class StationCatalog {

    public static final List<Station> ALL = List.of(
            new Station("QUITO", 14.5, 2850),
            new Station("GUAYAQUIL", 26.5, 4),
            new Station("CUENCA", 15.0, 2560),
            new Station("LOJA", 16.5, 2060),
            new Station("AMBATO", 14.0, 2577),
            new Station("MANTA", 25.5, 6),
            new Station("ESMERALDAS", 26.0, 15),
            new Station("IBARRA", 16.5, 2225),
            new Station("RIOBAMBA", 13.0, 2754),
            new Station("LATACUNGA", 13.5, 2750),
            new Station("TULCAN", 11.0, 2950),
            new Station("PUYO", 22.0, 960)
    );

    private StationCatalog() {
    }

    public static List<String> codes() {
        return ALL.stream().map(Station::code).toList();
    }

    /** Normaliza el código y lanza 404 si no existe. */
    public static String require(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        boolean known = ALL.stream().anyMatch(s -> s.code().equals(code));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estación desconocida: " + raw);
        }
        return code;
    }
}
