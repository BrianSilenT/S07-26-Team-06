package com.benchmark.datacenter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Un punto (dimension, percentil) -> valor de la curva de referencia
 * publica. Vive en la DB (no hardcodeada) para poder recalibrarse
 * desde el Table Editor de Supabase sin redeploy -- ver V2__public_reference_curves.sql
 * y SOURCES.md para la trazabilidad de cada valor.
 */
@Entity
@Table(name = "public_reference_curves")
@IdClass(PublicReferenceCurvePointEntity.PointId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicReferenceCurvePointEntity {

    @Id
    private String dimension;

    @Id
    private Short percentile;

    @Column(nullable = false)
    private Short value;

    private String source;

    private Instant updatedAt;

    public static class PointId implements Serializable {
        private String dimension;
        private Short percentile;

        public PointId() {}
        public PointId(String dimension, Short percentile) {
            this.dimension = dimension;
            this.percentile = percentile;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PointId)) return false;
            PointId that = (PointId) o;
            return dimension.equals(that.dimension) && percentile.equals(that.percentile);
        }

        @Override
        public int hashCode() {
            return dimension.hashCode() * 31 + percentile.hashCode();
        }
    }
}
