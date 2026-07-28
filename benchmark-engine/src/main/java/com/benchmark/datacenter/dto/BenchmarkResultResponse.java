package com.benchmark.datacenter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Output personalizado que recibe el operador. Todo lo que aqui
 * es un percentil viene ya mezclado (publico + primario) por
 * RebalancingService; nunca se expone el percentil "solo publico"
 * o "solo primario" por separado al usuario final.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkResultResponse {

    private UUID operatorId;

    private DimensionPercentiles percentiles;

    /** Cual de las 3 dimensiones numericas es la mas debil relativa al resto del mercado. */
    private String weakestDimension;

    /** Interfaz de friccion que el propio operador reporto. */
    private String frictionAttribution;

    /** Bloqueante principal reportado. */
    private String primaryBlocker;

    /** Perfil cualitativo generado a partir de la combinacion de percentiles + friccion + bloqueante. */
    private String qualitativeProfile;

    /** Que hacen distinto los operadores del cuartil superior en la dimension mas debil del operador. */
    private String topQuartileInsight;

    /** Transparencia sobre cuanto peso tuvo dato primario vs publico al momento de este calculo. */
    private RebalancingMetadata rebalancingMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionPercentiles {
        private Integer visibility;
        private Integer coordinationLatency;
        private Integer selfQuantification;
        private Integer composite;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RebalancingMetadata {
        private long primarySampleSize;
        private double primaryWeight; // 0-1, cuanto peso tuvo el dataset primario en el blend
    }
}
