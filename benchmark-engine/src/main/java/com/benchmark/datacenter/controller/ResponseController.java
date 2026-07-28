package com.benchmark.datacenter.controller;

import com.benchmark.datacenter.dto.BenchmarkSubmissionRequest;
import com.benchmark.datacenter.dto.BenchmarkSubmissionResponse;
import com.benchmark.datacenter.service.BenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/responses")
@RequiredArgsConstructor
@Tag(name = "Responses", description = "Envio de diagnosticos del benchmark")
public class ResponseController {

    private final BenchmarkService benchmarkService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recibe las respuestas del formulario, calcula el score y actualiza el dataset agregado")
    public BenchmarkSubmissionResponse submit(@Valid @RequestBody BenchmarkSubmissionRequest request) {
        return benchmarkService.submit(request);
    }
}
