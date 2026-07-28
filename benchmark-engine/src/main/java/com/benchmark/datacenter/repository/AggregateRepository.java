package com.benchmark.datacenter.repository;

import com.benchmark.datacenter.entity.AggregateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AggregateRepository extends JpaRepository<AggregateEntity, UUID> {

    Optional<AggregateEntity> findByDimensionAndSegmentKey(String dimension, String segmentKey);

    List<AggregateEntity> findBySegmentKey(String segmentKey);

    /**
     * Upsert atomico: crea la fila si no existe, o incrementa
     * count/sum/sumSq si ya existe. Evita condiciones de carrera
     * entre lecturas y escrituras concurrentes del rollup.
     */
    @Modifying
    @Query(value = """
        insert into aggregates (id, dimension, segment_key, sample_count, sum_value, sum_sq_value, category_counts, updated_at)
        values (gen_random_uuid(), :dimension, :segmentKey, 1, :value, :valueSq, '{}'::jsonb, now())
        on conflict (dimension, segment_key)
        do update set
            sample_count = aggregates.sample_count + 1,
            sum_value = aggregates.sum_value + :value,
            sum_sq_value = aggregates.sum_sq_value + :valueSq,
            updated_at = now()
        """, nativeQuery = true)
    void upsertIncrement(@Param("dimension") String dimension,
                          @Param("segmentKey") String segmentKey,
                          @Param("value") BigDecimal value,
                          @Param("valueSq") BigDecimal valueSq);

    @Modifying
    @Query(value = """
        insert into aggregates (id, dimension, segment_key, sample_count, sum_value, sum_sq_value, category_counts, updated_at)
        values (gen_random_uuid(), :dimension, :segmentKey, 0, 0, 0,
                jsonb_build_object(:category, 1), now())
        on conflict (dimension, segment_key)
        do update set
            category_counts = jsonb_set(
                aggregates.category_counts,
                array[:category],
                to_jsonb(coalesce((aggregates.category_counts ->> :category)::int, 0) + 1)
            ),
            updated_at = now()
        """, nativeQuery = true)
    void upsertCategoryIncrement(@Param("dimension") String dimension,
                                  @Param("segmentKey") String segmentKey,
                                  @Param("category") String category);
}
