package com.benchmark.datacenter.repository;

import com.benchmark.datacenter.entity.ScoreSampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ScoreSampleRepository extends JpaRepository<ScoreSampleEntity, Long> {

    @Query("select s.value from ScoreSampleEntity s where s.dimension = :dimension and s.segmentKey = :segmentKey order by s.value asc")
    List<BigDecimal> findSortedValues(@Param("dimension") String dimension, @Param("segmentKey") String segmentKey);

    long countByDimensionAndSegmentKey(String dimension, String segmentKey);
}
