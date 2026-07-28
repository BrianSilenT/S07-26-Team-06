package com.benchmark.datacenter.service;

import com.benchmark.datacenter.dto.BenchmarkResultResponse.DimensionPercentiles;
import com.benchmark.datacenter.entity.Dimension;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Genera el texto que hace que el output se sienta "especifico, no
 * generico" (criterio de exito del brief). La logica es: 1) identificar
 * la dimension mas debil relativa al mercado (no en absoluto -- un
 * operador puede tener self-quantification en p60 y aun asi ser su
 * dimension mas debil si visibility esta en p90), 2) combinar esa
 * dimension con la friccion/bloqueante auto-reportado para dar un
 * perfil que se siente hecho a medida, 3) describir que hace distinto
 * el cuartil superior EN ESA DIMENSION especifica (no un insight generico).
 */
@Service
public class InsightService {

    public Dimension weakestDimension(DimensionPercentiles p) {
        Map<Dimension, Integer> scores = Map.of(
                Dimension.VISIBILITY, p.getVisibility(),
                Dimension.COORDINATION_LATENCY, p.getCoordinationLatency(),
                Dimension.SELF_QUANTIFICATION, p.getSelfQuantification()
        );
        return scores.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Dimension.COMPOSITE);
    }

    public String qualitativeProfile(Dimension weakest, DimensionPercentiles p, String frictionAttribution, String primaryBlocker) {
        String frictionLabel = humanize(frictionAttribution);
        String blockerLabel = humanize(primaryBlocker);

        return switch (weakest) {
            case VISIBILITY -> String.format(Locale.ROOT,
                    "Tu punto mas debil relativo al mercado es visibilidad cross-layer (percentil %d): " +
                    "operas con vistas separadas de energia, cooling y workloads. Reportas que la friccion " +
                    "principal esta en %s, lo cual es consistente -- sin una vista unificada es dificil " +
                    "detectar esa interfaz en tiempo real, no solo despues del hecho.",
                    p.getVisibility(), frictionLabel);
            case COORDINATION_LATENCY -> String.format(Locale.ROOT,
                    "Tu punto mas debil relativo al mercado es la velocidad de coordinacion (percentil %d): " +
                    "cuando cambia el workload, cooling y energia tardan mas en ajustarse que en la mayoria " +
                    "del mercado. Combinado con %s como bloqueante principal, esto sugiere que el problema " +
                    "no es de deteccion sino de proceso de respuesta.",
                    p.getCoordinationLatency(), blockerLabel);
            case SELF_QUANTIFICATION -> String.format(Locale.ROOT,
                    "Tu punto mas debil relativo al mercado es la auto-cuantificacion (percentil %d): " +
                    "no tienes (o no tienes reciente) una medicion de cuanta capacidad pagada no esta " +
                    "produciendo. Esto suele preceder a la friccion en %s -- es dificil priorizar arreglar " +
                    "una interfaz cuya perdida de capacidad no esta cuantificada.",
                    p.getSelfQuantification(), frictionLabel);
            default -> "Tus tres dimensiones estan relativamente parejas frente al mercado.";
        };
    }

    public String topQuartileInsight(Dimension weakest) {
        return switch (weakest) {
            case VISIBILITY -> "Los operadores del cuartil superior en visibilidad integran telemetria de " +
                    "energia, cooling y workloads en un solo panel actualizado en tiempo real (no en reportes " +
                    "diarios o manuales), lo que les permite ver la friccion cross-layer antes de que se " +
                    "traduzca en capacidad ociosa.";
            case COORDINATION_LATENCY -> "Los operadores del cuartil superior en latencia de coordinacion " +
                    "resuelven ajustes cooling-energia en minutos, no horas, porque automatizaron el trigger " +
                    "(no dependen de un ticket manual entre equipos separados).";
            case SELF_QUANTIFICATION -> "Los operadores del cuartil superior en auto-cuantificacion miden su " +
                    "stranded capacity de forma continua (no puntual) y reportan reducciones de entre 10-15% " +
                    "de capacidad ociosa en los 12 meses posteriores a empezar a medirla.";
            default -> "Los operadores del cuartil superior mantienen las tres dimensiones coordinadas entre si, " +
                    "en vez de optimizar una a expensas de las otras.";
        };
    }

    private String humanize(String enumName) {
        if (enumName == null) return "sin especificar";
        return enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
