package com.benchmark.datacenter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Payload de POST /responses. Cada sub-objeto corresponde a una
 * de las 5 dimensiones del benchmark descritas en el brief.
 */
@Data
public class BenchmarkSubmissionRequest {

    @NotNull(message = "facilitySizeBucket es requerido")
    private AnswerOptions.FacilitySizeBucket facilitySizeBucket;

    @NotNull(message = "industryVertical es requerido")
    private AnswerOptions.IndustryVertical industryVertical;

    @NotNull(message = "region es requerida")
    private AnswerOptions.Region region;

    @Valid @NotNull
    private VisibilityAnswers visibility;

    @NotNull(message = "frictionAttribution es requerido")
    private AnswerOptions.FrictionInterface frictionAttribution;

    @NotNull(message = "coordinationLatency es requerido")
    private AnswerOptions.CoordinationLatencyBucket coordinationLatency;

    @Valid @NotNull
    private SelfQuantificationAnswers selfQuantification;

    @NotNull(message = "primaryBlocker es requerido")
    private AnswerOptions.Blocker primaryBlocker;

    /** Dimension 1: Visibilidad cross-layer. */
    @Data
    public static class VisibilityAnswers {
        @NotNull
        private Boolean hasUnifiedView;

        @NotNull
        private AnswerOptions.DataUpdateFrequency dataUpdateFrequency;

        @NotNull
        @Min(0) @Max(4)
        private Integer toolsIntegratedCount; // cuantas de {energia, cooling, workloads, capacity planning} estan integradas
    }

    /** Dimension 4: Auto-cuantificacion de stranded capacity. */
    @Data
    public static class SelfQuantificationAnswers {
        @NotNull
        private Boolean knowsStrandedCapacityPct;

        @DecimalMin("0.0") @DecimalMax("100.0")
        private Double estimatedStrandedCapacityPct; // null si knowsStrandedCapacityPct = false

        /** Dias desde la ultima medicion. null si nunca la midieron. */
        @Min(0)
        private Integer daysSinceLastMeasurement;
    }
}
