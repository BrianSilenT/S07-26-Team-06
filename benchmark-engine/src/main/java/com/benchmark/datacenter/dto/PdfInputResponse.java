package com.benchmark.datacenter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Formato de salida consumido por el generador de PDF (Proyecto 5).
 * A diferencia de BenchmarkResultResponse, aqui separamos
 * explicitamente los valores numericos (percentiles) de los
 * categoricos (friccion, bloqueante) para que el template del PDF
 * no tenga que inspeccionar tipos dentro de un mismo objeto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfInputResponse {

    @JsonProperty("operator_id")
    private UUID operatorId;

    @JsonProperty("percentiles")
    private Percentiles percentiles;

    @JsonProperty("friction_attribution")
    private String frictionAttribution;

    @JsonProperty("primary_blocker")
    private String primaryBlocker;

    @JsonProperty("friccion_principal")
    private String frictionPrincipal; // etiqueta de la dimension mas debil, ej. "auto-cuantificacion"

    @JsonProperty("insight_cuartil_superior")
    private String insightCuartilSuperior;

    @JsonProperty("perfil_cualitativo")
    private String perfilCualitativo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Percentiles {
        private Integer visibility;

        @JsonProperty("coordination_latency")
        private Integer coordinationLatency;

        @JsonProperty("self_quantification")
        private Integer selfQuantification;

        private Integer composite;
    }
}
