package com.benchmark.datacenter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de aplicar el rubric de scoring a una respuesta.
 * Relacion 1:1 con ResponseEntity via responseId (unique).
 */
@Entity
@Table(name = "scores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "response_id", nullable = false, unique = true)
    private UUID responseId;

    @Column(name = "visibility_score", nullable = false)
    private BigDecimal visibilityScore;

    @Column(name = "friction_attribution", nullable = false)
    private String frictionAttribution;

    @Column(name = "coordination_latency_score", nullable = false)
    private BigDecimal coordinationLatencyScore;

    @Column(name = "self_quantification_score", nullable = false)
    private BigDecimal selfQuantificationScore;

    @Column(name = "primary_blocker", nullable = false)
    private String primaryBlocker;

    @Column(name = "composite_score", nullable = false)
    private BigDecimal compositeScore;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
