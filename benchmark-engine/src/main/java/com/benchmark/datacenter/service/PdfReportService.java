package com.benchmark.datacenter.service;

import com.benchmark.datacenter.dto.BenchmarkResultResponse;
import com.lowagie.text.Element;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Genera el reporte en PDF a partir del mismo BenchmarkResultResponse
 * que ya arma BenchmarkService.getResults() -- no duplica ninguna
 * logica de scoring/percentiles/insights, solo la presenta como PDF.
 *
 * Estilo: documento clasico tipo Word/Google Docs -- fondo blanco,
 * tipografia limpia, tablas con bordes finos, un solo color de acento
 * (azul) para titulos. Nada de tema oscuro ni caracteres unicode
 * decorativos (Helvetica base no los soporta de forma confiable en PDF).
 *
 * Usa OpenPDF (fork libre de iText 4, licencia LGPL/MPL) en vez de
 * iText 5+ para evitar el requisito de licencia comercial de AGPL.
 */
@Service
public class PdfReportService {

    private static final Color BLUE = new Color(0x11, 0x55, 0xCC);      // acento, estilo link de Google Docs
    private static final Color INK = new Color(0x20, 0x22, 0x26);       // texto principal, no negro puro
    private static final Color GRAY = new Color(0x5F, 0x63, 0x68);      // texto secundario
    private static final Color LIGHT_GRAY_BG = new Color(0xF3, 0xF4, 0xF6);
    private static final Color BORDER = new Color(0xDA, 0xDC, 0xE0);
    private static final Color BAR_FILL = new Color(0x11, 0x55, 0xCC);
    private static final Color BAR_EMPTY = new Color(0xE6, 0xE8, 0xEB);
    private static final Color AMBER_TEXT = new Color(0x9A, 0x62, 0x00);
    private static final Color GREEN_TEXT = new Color(0x1E, 0x7E, 0x5A);

    private static final Font F_TITLE = new Font(Font.HELVETICA, 20, Font.BOLD, INK);
    private static final Font F_META = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, GRAY);
    private static final Font F_H2 = new Font(Font.HELVETICA, 13, Font.BOLD, BLUE);
    private static final Font F_BODY = new Font(Font.HELVETICA, 10.5f, Font.NORMAL, INK);
    private static final Font F_BODY_BOLD = new Font(Font.HELVETICA, 10.5f, Font.BOLD, INK);
    private static final Font F_SMALL_GRAY = new Font(Font.HELVETICA, 9, Font.NORMAL, GRAY);
    private static final Font F_TABLE_HEAD = new Font(Font.HELVETICA, 9.5f, Font.BOLD, GRAY);
    private static final Font F_TABLE_CELL = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
    private static final Font F_BIG_NUM = new Font(Font.HELVETICA, 34, Font.BOLD, BLUE);
    private static final Font F_CALLOUT_LABEL_AMBER = new Font(Font.HELVETICA, 9.5f, Font.BOLD, AMBER_TEXT);
    private static final Font F_CALLOUT_LABEL_GREEN = new Font(Font.HELVETICA, 9.5f, Font.BOLD, GREEN_TEXT);

    public byte[] generate(BenchmarkResultResponse result) {
        Document document = new Document(PageSize.A4, 54, 54, 60, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent());
            document.open();

            addHeader(document, result);
            addSummaryBox(document, result);
            addDimensionTable(document, result);
            addAttributionRow(document, result);
            addCallout(document, "PUNTO MÁS DÉBIL RELATIVO AL MERCADO", result.getQualitativeProfile(), F_CALLOUT_LABEL_AMBER);
            addCallout(document, "QUÉ HACE DISTINTO EL CUARTIL SUPERIOR", result.getTopQuartileInsight(), F_CALLOUT_LABEL_GREEN);
            addRebalancingNote(document, result);

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("No se pudo generar el PDF del reporte", e);
        }

        return out.toByteArray();
    }

    private void addHeader(Document document, BenchmarkResultResponse result) throws DocumentException {
        Paragraph title = new Paragraph("Reporte de diagnóstico", F_TITLE);
        title.setSpacingAfter(3);
        document.add(title);

        Paragraph subtitle = new Paragraph("Benchmark de coordinación de infraestructura", F_META);
        subtitle.setSpacingAfter(10);
        document.add(subtitle);

        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy"));
        Paragraph meta = new Paragraph("Operador " + shortId(result.getOperatorId().toString()) + "   |   " + fecha, F_META);
        meta.setSpacingAfter(14);
        document.add(meta);

        LineSeparator sep = new LineSeparator(0.75f, 100, BORDER, Element.ALIGN_LEFT, -2);
        document.add(new Chunk(sep));
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(4);
        document.add(spacer);
    }

    private void addSummaryBox(Document document, BenchmarkResultResponse result) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingBefore(6);
        box.setSpacingAfter(20);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_GRAY_BG);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.75f);
        cell.setPadding(16);

        Paragraph label = new Paragraph("PERCENTIL COMPUESTO", F_TABLE_HEAD);
        label.setSpacingAfter(2);
        cell.addElement(label);

        Paragraph num = new Paragraph(String.valueOf(result.getPercentiles().getComposite()), F_BIG_NUM);
        num.setSpacingAfter(4);
        cell.addElement(num);

        Paragraph desc = new Paragraph(
                "Estás por delante del " + result.getPercentiles().getComposite() +
                        "% de operadores comparables en coordinación general de energía, cooling y workloads.",
                F_BODY);
        cell.addElement(desc);

        box.addCell(cell);
        document.add(box);
    }

    private void addDimensionTable(Document document, BenchmarkResultResponse result) throws DocumentException {
        Paragraph h2 = new Paragraph("Percentil por dimensión", F_H2);
        h2.setSpacingAfter(10);
        document.add(h2);

        PdfPTable table = new PdfPTable(new float[]{2.4f, 1f, 3.2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);

        addHeadCell(table, "Dimensión");
        addHeadCell(table, "Percentil");
        addHeadCell(table, "");

        addDimensionRow(table, "Visibilidad cross-layer", result.getPercentiles().getVisibility());
        addDimensionRow(table, "Latencia de coordinación", result.getPercentiles().getCoordinationLatency());
        addDimensionRow(table, "Auto-cuantificación", result.getPercentiles().getSelfQuantification());

        document.add(table);
    }

    private void addHeadCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, F_TABLE_HEAD));
        cell.setBackgroundColor(LIGHT_GRAY_BG);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.75f);
        cell.setPadding(7);
        table.addCell(cell);
    }

    private void addDimensionRow(PdfPTable table, String name, int percentile) {
        PdfPCell nameCell = new PdfPCell(new Phrase(name, F_TABLE_CELL));
        nameCell.setBorderColor(BORDER);
        nameCell.setBorderWidth(0.75f);
        nameCell.setPadding(8);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(nameCell);

        PdfPCell valueCell = new PdfPCell(new Phrase("p" + percentile, F_BODY_BOLD));
        valueCell.setBorderColor(BORDER);
        valueCell.setBorderWidth(0.75f);
        valueCell.setPadding(8);
        valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(valueCell);

        PdfPCell barCell = new PdfPCell();
        barCell.setBorderColor(BORDER);
        barCell.setBorderWidth(0.75f);
        barCell.setPadding(10);
        barCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        barCell.addElement(buildBar(percentile));
        table.addCell(barCell);
    }

    /** Barra de progreso hecha con una tabla anidada de 2 celdas coloreadas -- sin depender de glifos unicode. */
    private PdfPTable buildBar(int percentile) {
        int filled = Math.max(1, Math.min(99, percentile));
        PdfPTable bar = new PdfPTable(new float[]{filled, 100 - filled});
        bar.setWidthPercentage(100);

        PdfPCell filledCell = new PdfPCell();
        filledCell.setBackgroundColor(BAR_FILL);
        filledCell.setBorder(Rectangle.NO_BORDER);
        filledCell.setFixedHeight(8);
        bar.addCell(filledCell);

        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBackgroundColor(BAR_EMPTY);
        emptyCell.setBorder(Rectangle.NO_BORDER);
        emptyCell.setFixedHeight(8);
        bar.addCell(emptyCell);

        return bar;
    }

    private void addAttributionRow(Document document, BenchmarkResultResponse result) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk("Fricción principal:  ", F_SMALL_GRAY));
        p.add(new Chunk(humanize(result.getFrictionAttribution()), F_BODY_BOLD));
        p.add(new Chunk("      Bloqueante principal:  ", F_SMALL_GRAY));
        p.add(new Chunk(humanize(result.getPrimaryBlocker()), F_BODY_BOLD));
        p.setSpacingAfter(20);
        document.add(p);
    }

    private void addCallout(Document document, String label, String body, Font labelFont) throws DocumentException {
        Paragraph l = new Paragraph(label, labelFont);
        l.setSpacingAfter(5);
        document.add(l);

        Paragraph text = new Paragraph(body != null ? body : "", F_BODY);
        text.setAlignment(Element.ALIGN_JUSTIFIED);
        text.setSpacingAfter(16);
        document.add(text);
    }

    private void addRebalancingNote(Document document, BenchmarkResultResponse result) throws DocumentException {
        LineSeparator sep = new LineSeparator(0.5f, 100, BORDER, Element.ALIGN_LEFT, -2);
        document.add(new Chunk(sep));

        BenchmarkResultResponse.RebalancingMetadata rb = result.getRebalancingMetadata();
        Paragraph note = new Paragraph(
                "Este percentil combina la curva de referencia pública con " + rb.getPrimarySampleSize() +
                        " respuestas primarias reales acumuladas (peso del dato primario: " +
                        Math.round(rb.getPrimaryWeight() * 100) +
                        "%). Tu respuesta individual nunca se comparte -- solo alimenta el dataset agregado y anónimo.",
                F_SMALL_GRAY);
        note.setSpacingBefore(10);
        document.add(note);
    }

    private String humanize(String enumValue) {
        if (enumValue == null) return "sin especificar";
        return enumValue.toLowerCase().replace('_', ' ');
    }

    private String shortId(String uuid) {
        return uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
    }

    /** Pie de pagina con numero de pagina, estilo documento formal. */
    private static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Phrase footer = new Phrase("Página " + writer.getPageNumber(), F_SMALL_GRAY);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_RIGHT, footer,
                    document.right(), document.bottom() - 20, 0);
        }
    }
}
