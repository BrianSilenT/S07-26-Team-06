package com.benchmark.datacenter.service;

import com.benchmark.datacenter.entity.Dimension;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Distribucion de referencia PUBLICA por dimension, usada para dar
 * output desde el dia 1 (antes de tener datos primarios) y como el
 * "ancla" que el rebalanceo va diluyendo a medida que N crece.
 *
 * *** PLACEHOLDER ***
 * Los breakpoints de abajo son estimaciones razonadas a partir de
 * literatura publica de la industria sobre stranded capacity y
 * madurez operativa de data centers (no una calibracion formal).
 * Reemplazar en Fase 1 con un analisis real de fuentes publicas
 * (reportes de Uptime Institute, papers de eficiencia operativa, etc.)
 * antes de publicar el benchmark. La forma de la curva (sesgada hacia
 * valores bajos, ya que la premisa del producto es que la mayoria de
 * la industria NO coordina bien estas capas) si es intencional.
 *
 * Cada array tiene 11 valores = el valor de la dimension en los
 * percentiles 0,10,20,...,100 (interpolacion lineal entre puntos).
 */
@Component
public class PublicReferenceCurves {

    private static final Map<Dimension, int[]> CURVES = Map.of(
            Dimension.VISIBILITY,            new int[]{0, 5, 10, 15, 22, 30, 40, 52, 65, 80, 100},
            Dimension.COORDINATION_LATENCY,  new int[]{0, 10, 18, 25, 32, 40, 50, 62, 75, 88, 100},
            Dimension.SELF_QUANTIFICATION,   new int[]{0, 5, 8, 12, 18, 25, 35, 48, 62, 80, 100},
            Dimension.COMPOSITE,             new int[]{0, 7, 12, 18, 25, 33, 42, 54, 67, 82, 100}
    );

    public int[] curveFor(Dimension dimension) {
        return CURVES.get(dimension);
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
