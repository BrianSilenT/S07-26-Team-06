package com.benchmark.datacenter.repository;

import com.benchmark.datacenter.entity.ResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResponseRepository extends JpaRepository<ResponseEntity, UUID> {
}
