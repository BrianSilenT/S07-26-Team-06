package com.benchmark.datacenter.repository;

import com.benchmark.datacenter.entity.ScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoreRepository extends JpaRepository<ScoreEntity, UUID> {
    Optional<ScoreEntity> findByResponseId(UUID responseId);
}
