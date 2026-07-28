package com.benchmark.datacenter.controller;

import com.benchmark.datacenter.dto.PdfInputResponse;
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
@RequestMapping("/pdf-input")
@RequiredArgsConstructor
@Tag(name = "PDF Input", description = "Input estructurado consumido por el generador de PDF (Proyecto 5)")
public class PdfInputController {

    private final BenchmarkService benchmarkService;

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve el JSON que consume el generador de PDF para un operador")
    public PdfInputResponse getPdfInput(@PathVariable("id") UUID id) {
        return benchmarkService.getPdfInput(id);
    }
}
