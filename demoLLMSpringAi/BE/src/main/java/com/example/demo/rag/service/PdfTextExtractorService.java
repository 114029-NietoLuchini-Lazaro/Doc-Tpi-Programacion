package com.example.demo.rag.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PdfTextExtractorService {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractorService.class);

    /** Página con menos de estos caracteres útiles se considera escaneada (sin texto extraíble). */
    private static final int SCANNED_PAGE_MIN_CHARS = 40;

    /** Fracción mínima de páginas donde debe repetirse una línea para considerarla header/footer. */
    private static final double REPEATED_LINE_THRESHOLD = 0.4;

    private static final Pattern DEHYPHEN_PATTERN = Pattern.compile("([\\p{L}])-\\n([\\p{L}])");
    private static final Pattern PAGE_NUMBER_LINE_PATTERN = Pattern.compile("\\d{1,5}");
    private static final Pattern PAGE_LABEL_PATTERN = Pattern.compile("(?i)página\\s+\\d{1,5}");

    public record ExtractedPage(int pageNumber, String text, int charCount, boolean isScannedLikely) {}

    public record ExtractedPdf(int totalPages, List<ExtractedPage> pages, String fullText) {}

    /**
     * Extrae el texto de un PDF preservando la paginación para citaciones RAG precisas,
     * con limpieza de texto: de-hifenización, filtro de líneas repetidas (headers/footers)
     * y detección determinista de páginas escaneadas.
     */
    public ExtractedPdf extractTextWithPages(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("El archivo PDF está vacío o no contiene bytes válidos.");
        }

        List<String> rawPageTexts = new ArrayList<>();
        int totalPages;

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("El archivo PDF está protegido con contraseña. Por favor sube un PDF sin cifrar.");
            }

            totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new IllegalArgumentException("El archivo PDF no contiene páginas.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String rawPageText = stripper.getText(document);
                rawPageTexts.add(normalizePageText(rawPageText));
            }
        }

        // Detección global de líneas de layout repetidas (headers, footers, numeración)
        Set<String> repeatedLayoutLines = detectRepeatedLayoutLines(rawPageTexts);

        List<ExtractedPage> extractedPages = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();

        for (int i = 0; i < rawPageTexts.size(); i++) {
            String pageText = stripLayoutLines(rawPageTexts.get(i), repeatedLayoutLines).trim();
            if (pageText.isBlank()) {
                continue;
            }

            int charCount = pageText.replaceAll("\\s", "").length();
            boolean scannedLikely = charCount < SCANNED_PAGE_MIN_CHARS;

            extractedPages.add(new ExtractedPage(i + 1, pageText, charCount, scannedLikely));
            fullTextBuilder.append(pageText).append("\n\n");
        }

        log.info("PDF extraído exitosamente: {} páginas totales, {} con contenido textual",
                totalPages, extractedPages.size());

        return new ExtractedPdf(totalPages, extractedPages, fullTextBuilder.toString().trim());
    }

    /**
     * Une palabras partidas por guion de fin de línea (de-hifenización), luego normaliza
     * espacios y saltos de línea superfluos.
     */
    String normalizePageText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        normalized = DEHYPHEN_PATTERN.matcher(normalized).replaceAll("$1$2");
        return normalized
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" +", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Identifica líneas que se repiten en varias páginas (cabeceras, pies, numeración)
     * para excluirlas del contenido indexable.
     */
    Set<String> detectRepeatedLayoutLines(List<String> pageTexts) {
        int totalPages = pageTexts.size();
        if (totalPages < 2) {
            return Collections.emptySet();
        }

        Map<String, Integer> occurrences = new HashMap<>();
        for (String pageText : pageTexts) {
            Set<String> pageLines = pageText.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toSet());
            for (String line : pageLines) {
                occurrences.merge(line, 1, Integer::sum);
            }
        }

        int minAppearances = Math.max(2, (int) Math.ceil(totalPages * REPEATED_LINE_THRESHOLD));
        Set<String> repeated = new HashSet<>();
        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            String line = entry.getKey();
            int count = entry.getValue();
            if (count >= minAppearances || (count >= 2 && isPageNumberLine(line))) {
                repeated.add(line);
            }
        }
        return repeated;
    }

    private boolean isPageNumberLine(String line) {
        return PAGE_NUMBER_LINE_PATTERN.matcher(line).matches() || PAGE_LABEL_PATTERN.matcher(line).matches();
    }

    private String stripLayoutLines(String pageText, Set<String> repeatedLayoutLines) {
        if (repeatedLayoutLines.isEmpty()) {
            return pageText;
        }
        return pageText.lines()
                .filter(line -> !repeatedLayoutLines.contains(line.trim()))
                .collect(Collectors.joining("\n"));
    }
}