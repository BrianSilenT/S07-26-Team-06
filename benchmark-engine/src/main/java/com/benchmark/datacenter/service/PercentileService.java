package com.benchmark.datacenter.service;

import com.benchmark.datacenter.entity.Dimension;
import com.benchmark.datacenter.repository.ScoreSampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PercentileService {

    public static final String GLOBAL_SEGMENT = "global";

    private final ScoreSampleRepository scoreSampleRepository;
    private final PublicReferenceCurves publicCurves;
    private final RebalancingService rebalancingService;

    public record PercentileResult(int percentile, long primarySampleSize, double primaryWeight) {}

    /**
     * Percentil final (0-100) para un valor en una dimension+segmento,
     * mezclando la curva publica y la distribucion primaria empirica
     * segun el peso que determine RebalancingService.
     */
    public PercentileResult percentileFor(Dimension dimension, String segmentKey, double value) {
        List<BigDecimal> primaryValues = scoreSampleRepository.findSortedValues(dimension.key(), segmentKey);
        long n = primaryValues.size();

        int publicPercentile = publicCurves.percentileOf(dimension, value);

        if (n == 0) {
            return new PercentileResult(publicPercentile, 0, 0.0);
        }

        int primaryPercentile = empiricalPercentile(primaryValues, value);
        int blended = rebalancingService.blendPercentiles(publicPercentile, primaryPercentile, n);
        double w = rebalancingService.primaryWeight(n);

        return new PercentileResult(blended, n, w);
    }

    /** Percentil rank empirico: porcentaje de valores primarios <= value. */
    private int empiricalPercentile(List<BigDecimal> sortedValues, double value) {
        long countLessOrEqual = sortedValues.stream()
                .filter(v -> v.doubleValue() <= value)
                .count();
        return (int) Math.round((countLessOrEqual / (double) sortedValues.size()) * 100);
    }

    /**
     * Curva blendeada (p10,p25,p50,p75,p90) para exponer en /aggregates.
     * Si no hay datos primarios en el segmento, devuelve la curva publica pura.
     */
    public List<Integer> blendedCurve(Dimension dimension, String segmentKey) {
        List<BigDecimal> primaryValues = scoreSampleRepository.findSortedValues(dimension.key(), segmentKey);
        long n = primaryValues.size();
        double w = rebalancingService.primaryWeight(n);

        int[] targetPercentiles = {10, 25, 50, 75, 90};
        return java.util.Arrays.stream(targetPercentiles)
                .mapToObj(p -> {
                    double publicVal = publicCurves.valueAtPercentile(dimension, p);
                    double primaryVal = n == 0 ? publicVal : empiricalValueAtPercentile(primaryValues, p);
                    return (int) Math.round((1 - w) * publicVal + w * primaryVal);
                })
                .toList();
    }

    private double empiricalValueAtPercentile(List<BigDecimal> sortedValues, int percentile) {
        int idx = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        idx = Math.max(0, Math.min(sortedValues.size() - 1, idx));
        return sortedValues.get(idx).doubleValue();
    }
}
