package com.benchmark.datacenter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Muestra de valores por (dimension, segmento) usada para calcular
 * percentiles reales (no solo media/desvio asumiendo normalidad).
 * Se cappea a N_MAX_SAMPLES por segmento en la capa de aplicacion
 * (reservoir sampling simple) para que la tabla no crezca sin limite.
 */
@Entity
@Table(name = "score_samples")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String dimension;

    @Column(name = "segment_key", nullable = false)
    private String segmentKey;

    @Column(nullable = false)
    private BigDecimal value;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
