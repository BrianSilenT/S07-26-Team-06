package com.benchmark.datacenter.entity;

/**
 * Las 3 dimensiones que se scorean como valores numericos 0-100
 * y por tanto tienen distribucion/percentil propio.
 *
 * "friction_attribution" y "blockers" son categoricas (no numericas):
 * se reportan como la interfaz/bloqueante mas frecuente, no como percentil.
 * COMPOSITE es el promedio ponderado de las tres, usado para el
 * percentil general del operador.
 */
public enum Dimension {
    VISIBILITY,
    COORDINATION_LATENCY,
    SELF_QUANTIFICATION,
    COMPOSITE;

    public String key() {
        return name().toLowerCase();
    }
}
