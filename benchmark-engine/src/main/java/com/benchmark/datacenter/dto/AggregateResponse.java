package com.benchmark.datacenter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Dataset agregado, sin nada individual. Pensado para consumo
 * externo (marketing del benchmark, research, etc.) por eso no
 * incluye ningun identificador de operador ni respuesta cruda.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregateResponse {

    private String segmentKey; // "global" o "industry:hyperscale", etc.
    private long sampleCount;

    private DimensionStats visibility;
    private DimensionStats coordinationLatency;
    private DimensionStats selfQuantification;
    private DimensionStats composite;

    private Map<String, Integer> frictionAttributionDistribution;
    private Map<String, Integer> primaryBlockerDistribution;

    private RebalancingState rebalancingState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionStats {
        private Double mean;
        private Double stdDev;
        private List<Integer> percentileCurve; // valor en p10, p25, p50, p75, p90
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RebalancingState {
        private long primarySampleSize;
        private double currentPrimaryWeight;
        private int smoothingFactorK;
    }
}
