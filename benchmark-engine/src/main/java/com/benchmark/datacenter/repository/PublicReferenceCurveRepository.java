package com.benchmark.datacenter.repository;

import com.benchmark.datacenter.entity.PublicReferenceCurvePointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicReferenceCurveRepository extends JpaRepository<PublicReferenceCurvePointEntity, PublicReferenceCurvePointEntity.PointId> {

    List<PublicReferenceCurvePointEntity> findByDimensionOrderByPercentileAsc(String dimension);
}
