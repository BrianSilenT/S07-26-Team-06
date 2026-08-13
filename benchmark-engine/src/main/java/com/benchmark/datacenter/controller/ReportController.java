package com.benchmark.datacenter.controller;

import com.benchmark.datacenter.dto.BenchmarkResultResponse;
import com.benchmark.datacenter.service.BenchmarkService;
import com.benchmark.datacenter.service.PdfReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Exportacion del resultado del operador como PDF")
public class ReportController {

    private final BenchmarkService benchmarkService;
    private final PdfReportService pdfReportService;

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Genera y descarga el reporte del operador en PDF")
    public ResponseEntity<byte[]> getPdf(@PathVariable("id") UUID id) {
        BenchmarkResultResponse result = benchmarkService.getResults(id);
        byte[] pdf = pdfReportService.generate(result);

        String filename = "benchmark-reporte-" + id.toString().substring(0, 8) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
