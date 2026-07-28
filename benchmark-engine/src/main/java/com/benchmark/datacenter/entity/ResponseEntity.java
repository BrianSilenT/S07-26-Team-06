package com.benchmark.datacenter.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Una fila = un diagnostico completado por un operador.
 * No contiene PII: solo metadata de segmento (coarse) y las
 * respuestas crudas del formulario, para poder re-scorear si
 * el rubric cambia sin volver a preguntar.
 */
@Entity
@Table(name = "responses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "facility_size_bucket", nullable = false)
    private String facilitySizeBucket;

    @Column(name = "industry_vertical", nullable = false)
    private String industryVertical;

    @Column(name = "region", nullable = false)
    private String region;

    @Type(JsonType.class)
    @Column(name = "raw_answers", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawAnswers;

    @Builder.Default
    @Column(name = "schema_version", nullable = false)
    private Short schemaVersion = 1;
}
