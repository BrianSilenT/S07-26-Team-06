package com.benchmark.datacenter.controller;

import com.benchmark.datacenter.dto.AggregateResponse;
import com.benchmark.datacenter.service.BenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aggregates")
@RequiredArgsConstructor
@Tag(name = "Aggregates", description = "Dataset agregado y anonimo")
public class AggregateController {

    private final BenchmarkService benchmarkService;

    @GetMapping
    @Operation(summary = "Devuelve estadisticas agregadas por dimension. Sin datos individuales.",
            description = "segment opcional: 'global' (default), 'industry:HYPERSCALE', 'region:LATAM', 'size:MW_1_5', etc.")
    public AggregateResponse getAggregates(
            @Parameter(description = "Segmento a consultar. Default: global")
            @RequestParam(name = "segment", required = false) String segment) {
        return benchmarkService.getAggregates(segment);
    }
}
