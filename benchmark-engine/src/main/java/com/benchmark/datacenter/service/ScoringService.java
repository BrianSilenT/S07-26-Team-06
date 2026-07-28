package com.benchmark.datacenter.service;

import com.benchmark.datacenter.dto.AnswerOptions.*;
import com.benchmark.datacenter.dto.BenchmarkSubmissionRequest;
import com.benchmark.datacenter.entity.ScoreEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Rubric de scoring v1. Cada regla esta documentada porque este es
 * el componente que mas se va a iterar: cambiar un peso aqui no
 * requiere tocar el resto del motor (percentiles, rebalanceo, output).
 *
 * IMPORTANTE: como responses.raw_answers guarda el payload crudo,
 * cualquier cambio de rubric permite re-scorear el historico
 * completo sin volver a preguntar a los operadores (ver ADR en README).
 */
@Service
public class ScoringService {

    private static final Map<DataUpdateFrequency, Integer> FREQUENCY_POINTS = Map.of(
            DataUpdateFrequency.REAL_TIME, 30,
            DataUpdateFrequency.HOURLY, 22,
            DataUpdateFrequency.DAILY, 15,
            DataUpdateFrequency.WEEKLY_OR_LESS, 8,
            DataUpdateFrequency.MANUAL_ONLY, 0
    );

    private static final Map<CoordinationLatencyBucket, Integer> LATENCY_POINTS = Map.of(
            CoordinationLatencyBucket.MINUTES, 100,
            CoordinationLatencyBucket.UNDER_1_HOUR, 80,
            CoordinationLatencyBucket.HOURS, 55,
            CoordinationLatencyBucket.DAYS, 30,
            CoordinationLatencyBucket.WEEKS_OR_MANUAL_TICKET, 10
    );

    /** Pesos del composite. Deben sumar 1.0. Configurable a futuro via application.yml. */
    private static final double W_VISIBILITY = 0.34;
    private static final double W_LATENCY = 0.33;
    private static final double W_SELF_QUANT = 0.33;

    public ScoreEntity score(UUID responseId, BenchmarkSubmissionRequest req) {
        BigDecimal visibility = scoreVisibility(req.getVisibility());
        BigDecimal latency = scoreLatency(req.getCoordinationLatency());
        BigDecimal selfQuant = scoreSelfQuantification(req.getSelfQuantification());

        BigDecimal composite = visibility.multiply(BigDecimal.valueOf(W_VISIBILITY))
                .add(latency.multiply(BigDecimal.valueOf(W_LATENCY)))
                .add(selfQuant.multiply(BigDecimal.valueOf(W_SELF_QUANT)))
                .setScale(2, RoundingMode.HALF_UP);

        return ScoreEntity.builder()
                .responseId(responseId)
                .visibilityScore(visibility)
                .frictionAttribution(req.getFrictionAttribution().name())
                .coordinationLatencyScore(latency)
                .selfQuantificationScore(selfQuant)
                .primaryBlocker(req.getPrimaryBlocker().name())
                .compositeScore(composite)
                .build();
    }

    /**
     * Dimension 1 - Visibilidad cross-layer (0-100):
     *  - 40 pts: tiene una vista unificada de energia+cooling+workloads
     *  - 30 pts: frecuencia de actualizacion de esos datos
     *  - 30 pts: cuantas de las 4 capas (energia, cooling, workloads, capacity planning) estan integradas
     */
    private BigDecimal scoreVisibility(BenchmarkSubmissionRequest.VisibilityAnswers a) {
        int points = 0;
        points += Boolean.TRUE.equals(a.getHasUnifiedView()) ? 40 : 0;
        points += FREQUENCY_POINTS.getOrDefault(a.getDataUpdateFrequency(), 0);
        points += (int) Math.round((a.getToolsIntegratedCount() / 4.0) * 30);
        return clamp(points);
    }

    /**
     * Dimension 3 - Latencia de coordinacion (0-100, mas alto = mas rapido).
     * Mapeo directo del bucket reportado.
     */
    private BigDecimal scoreLatency(CoordinationLatencyBucket bucket) {
        return clamp(LATENCY_POINTS.getOrDefault(bucket, 0));
    }

    /**
     * Dimension 4 - Auto-cuantificacion (0-100):
     *  - No sabe cuanta stranded capacity tiene -> 0-10 (10 si al menos reconoce que no lo sabe,
     *    en vez de asumir que no hay problema)
     *  - Sabe pero sin medicion reciente -> 40-60 segun antiguedad
     *  - Sabe y midio en los ultimos 90 dias -> 80-100
     */
    private BigDecimal scoreSelfQuantification(BenchmarkSubmissionRequest.SelfQuantificationAnswers a) {
        if (!Boolean.TRUE.equals(a.getKnowsStrandedCapacityPct())) {
            return clamp(5);
        }
        Integer days = a.getDaysSinceLastMeasurement();
        if (days == null) {
            return clamp(40); // sabe un numero pero no hay fecha de medicion confiable
        }
        if (days <= 90) {
            return clamp(100 - Math.min(20, days / 10)); // 80-100 dentro del trimestre
        } else if (days <= 365) {
            return clamp(60 - (days - 90) / 15); // decae hacia 40ish durante el resto del año
        } else {
            return clamp(30);
        }
    }

    private BigDecimal clamp(int rawPoints) {
        int bounded = Math.max(0, Math.min(100, rawPoints));
        return BigDecimal.valueOf(bounded).setScale(2, RoundingMode.HALF_UP);
    }
}
