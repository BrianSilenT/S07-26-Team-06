package com.benchmark.datacenter.controller;

import com.benchmark.datacenter.dto.BenchmarkResultResponse;
import com.benchmark.datacenter.service.BenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/results")
@RequiredArgsConstructor
@Tag(name = "Results", description = "Output personalizado por operador")
public class ResultController {

    private final BenchmarkService benchmarkService;

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve percentiles, friccion principal e insight de cuartil superior para un operador")
    public BenchmarkResultResponse getResults(@PathVariable("id") UUID id) {
        return benchmarkService.getResults(id);
    }
}
