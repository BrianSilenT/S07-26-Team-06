package com.benchmark.datacenter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Motor de rebalanceo dinamico publico/primario.
 *
 * Logica: peso_primario(N) = min( N / (N + k), max_primary_weight )
 *
 *  - N = numero de respuestas primarias acumuladas EN EL SEGMENTO relevante
 *        (no un N global unico -- ver nota en README sobre por que
 *        el rebalanceo debe ser por segmento/dimension, no global).
 *  - k = "smoothing factor": el N en el cual el peso primario llega a 50%.
 *        k alto = el motor confia mas lento en los datos primarios
 *        (util al inicio, cuando pocas respuestas podrian ser outliers).
 *  - max_primary_weight = tope duro para nunca depender 100% de datos
 *        primarios, incluso con miles de respuestas, como salvaguarda
 *        ante sesgos de quien completa el benchmark (self-selection bias).
 *
 * Por que esta forma funcional:
 *  - Es monotonamente creciente en N -> mas datos primarios siempre
 *    aumentan (o mantienen) su influencia, nunca la reducen.
 *  - Es suave (no hay un "salto" brusco de 100% publico a 100% primario
 *    en un umbral arbitrario), lo cual evita que el output de un
 *    operador cambie drasticamente por una sola respuesta nueva.
 *  - Es interpretable: con N = k, el peso primario es exactamente 50%.
 */
@Service
public class RebalancingService {

    private final int smoothingFactorK;
    private final double maxPrimaryWeight;

    public RebalancingService(
            @Value("${benchmark.rebalancing.smoothing-factor-k:50}") int smoothingFactorK,
            @Value("${benchmark.rebalancing.max-primary-weight:0.95}") double maxPrimaryWeight) {
        this.smoothingFactorK = smoothingFactorK;
        this.maxPrimaryWeight = maxPrimaryWeight;
    }

    /**
     * @param primarySampleSize N: respuestas primarias disponibles para el
     *                          segmento/dimension que se esta evaluando.
     * @return peso del dataset primario en el blend, entre 0 y maxPrimaryWeight.
     */
    public double primaryWeight(long primarySampleSize) {
        if (primarySampleSize <= 0) return 0.0;
        double raw = (double) primarySampleSize / (primarySampleSize + smoothingFactorK);
        return Math.min(raw, maxPrimaryWeight);
    }

    /**
     * Mezcla un percentil calculado contra la distribucion publica con
     * uno calculado contra la distribucion primaria, ponderado por
     * primaryWeight(N). Blend a nivel de percentil (no de distribucion
     * cruda) para poder usar cualquier fuente de referencia publica
     * sin tener que reconciliar sus unidades/formato con los datos primarios.
     */
    public int blendPercentiles(int publicPercentile, int primaryPercentile, long primarySampleSize) {
        double w = primaryWeight(primarySampleSize);
        double blended = (1 - w) * publicPercentile + w * primaryPercentile;
        return (int) Math.round(blended);
    }

    public int getSmoothingFactorK() {
        return smoothingFactorK;
    }

    public double getMaxPrimaryWeight() {
        return maxPrimaryWeight;
    }
}
