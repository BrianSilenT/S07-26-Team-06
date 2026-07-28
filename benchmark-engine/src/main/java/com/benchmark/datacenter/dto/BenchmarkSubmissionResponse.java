package com.benchmark.datacenter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BenchmarkSubmissionResponse {
    private UUID responseId;
    private String message;
    private String resultsUrl;
}
