package com.benchmark.datacenter.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Rollup incremental: una fila por (dimension, segmento).
 * segmentKey = "global" o "industry:hyperscale", "region:latam", etc.
 * Se actualiza en cada nueva respuesta (BenchmarkService) para poder
 * responder /aggregates y alimentar el motor de rebalanceo sin
 * escanear toda la tabla scores en cada request.
 */
@Entity
@Table(name = "aggregates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregateEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String dimension;

    @Column(name = "segment_key", nullable = false)
    private String segmentKey;

    @Builder.Default
    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount = 0;

    @Builder.Default
    @Column(name = "sum_value", nullable = false)
    private BigDecimal sumValue = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "sum_sq_value", nullable = false)
    private BigDecimal sumSqValue = BigDecimal.ZERO;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "category_counts", columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> categoryCounts = Map.of();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
