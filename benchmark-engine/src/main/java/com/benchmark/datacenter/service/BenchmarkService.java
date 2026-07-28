package com.benchmark.datacenter.service;

import com.benchmark.datacenter.dto.*;
import com.benchmark.datacenter.entity.*;
import com.benchmark.datacenter.exception.ResponseNotFoundException;
import com.benchmark.datacenter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final ResponseRepository responseRepository;
    private final ScoreRepository scoreRepository;
    private final AggregateRepository aggregateRepository;
    private final ScoreSampleRepository scoreSampleRepository;

    private final ScoringService scoringService;
    private final PercentileService percentileService;
    private final InsightService insightService;
    private final RebalancingService rebalancingService;

    /** Cuantas muestras crudas por (dimension, segmento) mantenemos para percentiles empiricos. */
    private static final long MAX_SAMPLES_PER_SEGMENT = 20_000;

    // ------------------------------------------------------------------
    // POST /responses
    // ------------------------------------------------------------------
    @Transactional
    public BenchmarkSubmissionResponse submit(BenchmarkSubmissionRequest req) {
        ResponseEntity response = ResponseEntity.builder()
                .facilitySizeBucket(req.getFacilitySizeBucket().name())
                .industryVertical(req.getIndustryVertical().name())
                .region(req.getRegion().name())
                .rawAnswers(toRawAnswersMap(req))
                .build();
        response = responseRepository.save(response);

        ScoreEntity score = scoringService.score(response.getId(), req);
        score = scoreRepository.save(score);

        updateAggregatesAndSamples(response, score);

        return new BenchmarkSubmissionResponse(
                response.getId(),
                "Diagnostico recibido. Tu posicion relativa ya esta disponible.",
                "/results/" + response.getId()
        );
    }

    private void updateAggregatesAndSamples(ResponseEntity response, ScoreEntity score) {
        List<String> segmentKeys = segmentKeysFor(response);

        Map<Dimension, BigDecimal> numericScores = Map.of(
                Dimension.VISIBILITY, score.getVisibilityScore(),
                Dimension.COORDINATION_LATENCY, score.getCoordinationLatencyScore(),
                Dimension.SELF_QUANTIFICATION, score.getSelfQuantificationScore(),
                Dimension.COMPOSITE, score.getCompositeScore()
        );

        for (String segmentKey : segmentKeys) {
            numericScores.forEach((dimension, value) -> {
                aggregateRepository.upsertIncrement(dimension.key(), segmentKey, value, value.multiply(value));
                addSample(dimension.key(), segmentKey, value);
            });

            aggregateRepository.upsertCategoryIncrement("friction_attribution", segmentKey, score.getFrictionAttribution());
            aggregateRepository.upsertCategoryIncrement("primary_blocker", segmentKey, score.getPrimaryBlocker());
        }
    }

    /** Reservoir sampling simplificado: si ya alcanzamos el cap, no seguimos insertando (MVP). */
    private void addSample(String dimension, String segmentKey, BigDecimal value) {
        long current = scoreSampleRepository.countByDimensionAndSegmentKey(dimension, segmentKey);
        if (current < MAX_SAMPLES_PER_SEGMENT) {
            scoreSampleRepository.save(ScoreSampleEntity.builder()
                    .dimension(dimension)
                    .segmentKey(segmentKey)
                    .value(value)
                    .build());
        }
        // TODO Fase 3+: reemplazar por reservoir sampling real (Algorithm R)
        // cuando current >= MAX_SAMPLES_PER_SEGMENT, para no sesgar hacia
        // las primeras respuestas recibidas.
    }

    private List<String> segmentKeysFor(ResponseEntity r) {
        return List.of(
                PercentileService.GLOBAL_SEGMENT,
                "industry:" + r.getIndustryVertical(),
                "region:" + r.getRegion(),
                "size:" + r.getFacilitySizeBucket()
        );
    }

    private Map<String, Object> toRawAnswersMap(BenchmarkSubmissionRequest req) {
        return Map.of(
                "visibility", req.getVisibility(),
                "frictionAttribution", req.getFrictionAttribution(),
                "coordinationLatency", req.getCoordinationLatency(),
                "selfQuantification", req.getSelfQuantification(),
                "primaryBlocker", req.getPrimaryBlocker()
        );
    }

    // ------------------------------------------------------------------
    // GET /results/{id}
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public BenchmarkResultResponse getResults(UUID responseId) {
        ScoreEntity score = scoreRepository.findByResponseId(responseId)
                .orElseThrow(() -> new ResponseNotFoundException(responseId));

        String segment = PercentileService.GLOBAL_SEGMENT; // MVP: percentil global. Extensible a segmentado.

        var visibilityP = percentileService.percentileFor(Dimension.VISIBILITY, segment, score.getVisibilityScore().doubleValue());
        var latencyP = percentileService.percentileFor(Dimension.COORDINATION_LATENCY, segment, score.getCoordinationLatencyScore().doubleValue());
        var selfQuantP = percentileService.percentileFor(Dimension.SELF_QUANTIFICATION, segment, score.getSelfQuantificationScore().doubleValue());
        var compositeP = percentileService.percentileFor(Dimension.COMPOSITE, segment, score.getCompositeScore().doubleValue());

        BenchmarkResultResponse.DimensionPercentiles percentiles = BenchmarkResultResponse.DimensionPercentiles.builder()
                .visibility(visibilityP.percentile())
                .coordinationLatency(latencyP.percentile())
                .selfQuantification(selfQuantP.percentile())
                .composite(compositeP.percentile())
                .build();

        Dimension weakest = insightService.weakestDimension(percentiles);

        return BenchmarkResultResponse.builder()
                .operatorId(responseId)
                .percentiles(percentiles)
                .weakestDimension(weakest.key())
                .frictionAttribution(score.getFrictionAttribution())
                .primaryBlocker(score.getPrimaryBlocker())
                .qualitativeProfile(insightService.qualitativeProfile(weakest, percentiles, score.getFrictionAttribution(), score.getPrimaryBlocker()))
                .topQuartileInsight(insightService.topQuartileInsight(weakest))
                .rebalancingMetadata(BenchmarkResultResponse.RebalancingMetadata.builder()
                        .primarySampleSize(compositeP.primarySampleSize())
                        .primaryWeight(round2(compositeP.primaryWeight()))
                        .build())
                .build();
    }

    // ------------------------------------------------------------------
    // GET /aggregates
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public AggregateResponse getAggregates(String segmentKey) {
        String segment = (segmentKey == null || segmentKey.isBlank()) ? PercentileService.GLOBAL_SEGMENT : segmentKey;

        List<AggregateEntity> rows = aggregateRepository.findBySegmentKey(segment);
        Map<String, AggregateEntity> byDimension = rows.stream()
                .filter(a -> !a.getDimension().equals("friction_attribution") && !a.getDimension().equals("primary_blocker"))
                .collect(Collectors.toMap(AggregateEntity::getDimension, a -> a));

        AggregateEntity frictionAgg = rows.stream().filter(a -> a.getDimension().equals("friction_attribution")).findFirst().orElse(null);
        AggregateEntity blockerAgg = rows.stream().filter(a -> a.getDimension().equals("primary_blocker")).findFirst().orElse(null);

        long sampleCount = byDimension.getOrDefault(Dimension.COMPOSITE.key(), AggregateEntity.builder().sampleCount(0).build()).getSampleCount();

        return AggregateResponse.builder()
                .segmentKey(segment)
                .sampleCount(sampleCount)
                .visibility(dimensionStats(byDimension.get(Dimension.VISIBILITY.key()), Dimension.VISIBILITY, segment))
                .coordinationLatency(dimensionStats(byDimension.get(Dimension.COORDINATION_LATENCY.key()), Dimension.COORDINATION_LATENCY, segment))
                .selfQuantification(dimensionStats(byDimension.get(Dimension.SELF_QUANTIFICATION.key()), Dimension.SELF_QUANTIFICATION, segment))
                .composite(dimensionStats(byDimension.get(Dimension.COMPOSITE.key()), Dimension.COMPOSITE, segment))
                .frictionAttributionDistribution(frictionAgg != null ? frictionAgg.getCategoryCounts() : Map.of())
                .primaryBlockerDistribution(blockerAgg != null ? blockerAgg.getCategoryCounts() : Map.of())
                .rebalancingState(AggregateResponse.RebalancingState.builder()
                        .primarySampleSize(sampleCount)
                        .currentPrimaryWeight(round2(rebalancingService.primaryWeight(sampleCount)))
                        .smoothingFactorK(rebalancingService.getSmoothingFactorK())
                        .build())
                .build();
    }

    private AggregateResponse.DimensionStats dimensionStats(AggregateEntity agg, Dimension dimension, String segment) {
        if (agg == null || agg.getSampleCount() == 0) {
            return AggregateResponse.DimensionStats.builder()
                    .mean(null)
                    .stdDev(null)
                    .percentileCurve(percentileService.blendedCurve(dimension, segment))
                    .build();
        }
        double mean = agg.getSumValue().doubleValue() / agg.getSampleCount();
        double variance = (agg.getSumSqValue().doubleValue() / agg.getSampleCount()) - (mean * mean);
        double stdDev = Math.sqrt(Math.max(0, variance));

        return AggregateResponse.DimensionStats.builder()
                .mean(round2(mean))
                .stdDev(round2(stdDev))
                .percentileCurve(percentileService.blendedCurve(dimension, segment))
                .build();
    }

    // ------------------------------------------------------------------
    // GET /pdf-input/{id}
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PdfInputResponse getPdfInput(UUID responseId) {
        BenchmarkResultResponse result = getResults(responseId);

        PdfInputResponse.Percentiles percentiles = PdfInputResponse.Percentiles.builder()
                .visibility(result.getPercentiles().getVisibility())
                .coordinationLatency(result.getPercentiles().getCoordinationLatency())
                .selfQuantification(result.getPercentiles().getSelfQuantification())
                .composite(result.getPercentiles().getComposite())
                .build();

        return PdfInputResponse.builder()
                .operatorId(result.getOperatorId())
                .percentiles(percentiles)
                .frictionAttribution(result.getFrictionAttribution())
                .primaryBlocker(result.getPrimaryBlocker())
                .frictionPrincipal(result.getWeakestDimension())
                .insightCuartilSuperior(result.getTopQuartileInsight())
                .perfilCualitativo(result.getQualitativeProfile())
                .build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
