package com.benchmark.datacenter.service;

import com.benchmark.datacenter.entity.Dimension;
import com.benchmark.datacenter.entity.PublicReferenceCurvePointEntity;
import com.benchmark.datacenter.repository.PublicReferenceCurveRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Distribucion de referencia PUBLICA por dimension, usada para dar
 * output desde el dia 1 (antes de tener datos primarios) y como el
 * "ancla" que el rebalanceo va diluyendo a medida que N crece.
 *
 * Los breakpoints viven en la tabla `public_reference_curves` (no
 * hardcodeados en Java) para poder recalibrarse desde el Table Editor
 * de Supabase sin redeploy. Se cargan una vez al arrancar la app y se
 * cachean en memoria -- si alguien edita la tabla, hace falta reiniciar
 * el backend para que tome el cambio (ver README, "Pendiente para Fase 1"
 * si se quiere hot-reload mas adelante).
 *
 * Trazabilidad de cada valor: ver SOURCES.md y el propio V2__public_reference_curves.sql
 * (fuente principal: Uptime Institute, "Global Data Center Survey 2025").
 *
 * Los arrays de DEFAULT_CURVES de abajo son un respaldo defensivo: si
 * por algun motivo la DB no tiene filas para una dimension (ej. antes
 * de correr la migracion V2), se usan estos valores en vez de romper.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PublicReferenceCurves {

    private static final Map<Dimension, int[]> DEFAULT_CURVES = Map.of(
            Dimension.VISIBILITY,            new int[]{0, 4, 8, 13, 19, 27, 37, 50, 64, 80, 100},
            Dimension.COORDINATION_LATENCY,  new int[]{0, 8, 15, 21, 28, 35, 45, 58, 72, 87, 100},
            Dimension.SELF_QUANTIFICATION,   new int[]{0, 3, 6, 9, 14, 20, 30, 43, 58, 78, 100},
            Dimension.COMPOSITE,             new int[]{0, 5, 10, 15, 21, 28, 38, 51, 65, 82, 100}
    );

    private final PublicReferenceCurveRepository repository;

    private final Map<Dimension, int[]> curves = new EnumMap<>(Dimension.class);

    @PostConstruct
    void loadFromDatabase() {
        for (Dimension dimension : Dimension.values()) {
            List<PublicReferenceCurvePointEntity> points = repository.findByDimensionOrderByPercentileAsc(dimension.key());

            if (points.size() != 11) {
                log.warn("public_reference_curves tiene {} filas para '{}' (se esperaban 11). " +
                                "Usando curva por defecto hardcodeada -- corre la migracion V2 o revisa la tabla.",
                        points.size(), dimension.key());
                curves.put(dimension, DEFAULT_CURVES.get(dimension));
                continue;
            }

            int[] curve = new int[11];
            for (PublicReferenceCurvePointEntity p : points) {
                curve[p.getPercentile() / 10] = p.getValue();
            }
            curves.put(dimension, curve);
        }
        log.info("Curvas de referencia publica cargadas desde la DB: {}", curves);
    }

    public int[] curveFor(Dimension dimension) {
        return curves.get(dimension);
    }

    /** Percentil (0-100) del valor dado contra la curva publica, por interpolacion lineal. */
    public int percentileOf(Dimension dimension, double value) {
        int[] curve = curveFor(dimension);
        if (value <= curve[0]) return 0;
        if (value >= curve[10]) return 100;

        for (int i = 0; i < 10; i++) {
            double lo = curve[i];
            double hi = curve[i + 1];
            if (value >= lo && value <= hi) {
                double fraction = (hi == lo) ? 0 : (value - lo) / (hi - lo);
                return (int) Math.round((i + fraction) * 10);
            }
        }
        return 50; // fallback defensivo, no deberia alcanzarse
    }

    /** Valor de la curva publica en un percentil dado (0-100), por interpolacion lineal. */
    public double valueAtPercentile(Dimension dimension, int percentile) {
        int[] curve = curveFor(dimension);
        int clamped = Math.max(0, Math.min(100, percentile));
        int lowerIdx = clamped / 10;
        if (lowerIdx == 10) return curve[10];
        double fraction = (clamped % 10) / 10.0;
        return curve[lowerIdx] + fraction * (curve[lowerIdx + 1] - curve[lowerIdx]);
    }
}
