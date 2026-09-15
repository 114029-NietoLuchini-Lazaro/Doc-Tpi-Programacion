package com.example.demo.rag.service;

import com.example.demo.rag.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RagContextService {

    /** Presupuesto máximo de caracteres del contexto ensamblado (aprox 3K tokens). */
    private static final int DEFAULT_MAX_CHARS = 12_000;

    /** Solape máximo esperado entre fragmentos consecutivos de la misma fuente. */
    private static final int MAX_OVERLAP_TO_STRIP = 200;

    public record ContextoResult(String texto, int nroChunksUsados, int paginasCubiertas) {}

    /**
     * Ensambla el contexto RAG final: orden estable (documento, página, índice),
     * deduplicación de solape entre fragmentos consecutivos, filtrado de contenido
     * duplicado y presupuesto de tokens (caracteres).
     */
    public ContextoResult ensamblarContexto(List<DocumentChunk> chunks) {
        return ensamblarContexto(chunks, DEFAULT_MAX_CHARS);
    }

    public ContextoResult ensamblarContexto(List<DocumentChunk> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return new ContextoResult("", 0, 0);
        }

        List<DocumentChunk> ordenados = new ArrayList<>(chunks);
        ordenados.sort(Comparator
                .comparing(DocumentChunk::getDocumentId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(DocumentChunk::getPageNumber)
                .thenComparingInt(DocumentChunk::getChunkIndex));

        StringBuilder builder = new StringBuilder();
        List<String> addedContents = new ArrayList<>();
        Set<String> paginasCubiertasSet = new HashSet<>();

        String lastSourceDoc = null;
        Integer lastSourcePage = null;
        String lastTail = null;
        boolean first = true;
        int usedChars = 0;
        int chunksUsados = 0;

        for (DocumentChunk chunk : ordenados) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            String effective = content.trim();

            // Deduplicación de solape: fragmentos consecutivos de la misma fuente comparten cola/cabeza
            boolean sameSourceStreak = chunk.getDocumentId().equals(lastSourceDoc)
                    && chunk.getPageNumber() == lastSourcePage;
            if (sameSourceStreak && lastTail != null) {
                int overlap = detectOverlap(lastTail, effective);
                if (overlap > 0 && overlap < effective.length()) {
                    effective = effective.substring(overlap).trim();
                    if (effective.isEmpty()) {
                        continue;
                    }
                }
            }

            // Filtro de duplicados: texto idéntico/repetido ya agregado (páginas que reiteran la figura)
            boolean duplicate = false;
            for (String added : addedContents) {
                if (added.contains(effective) || effective.contains(added)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                continue;
            }

            // Presupuesto de tokens: respeta al menos un fragmento y corta a partir del segundo que exceda
            if (usedChars + effective.length() > maxChars) {
                if (chunksUsados == 0 && builder.length() == 0) {
                    effective = effective.substring(0, maxChars);
                } else {
                    break;
                }
            }

            String docLabel = chunk.getDocumentName() != null ? chunk.getDocumentName() : "Documento";
            String bloque = String.format("[Fuente: \"%s\" | Página %d]: %s",
                    docLabel, chunk.getPageNumber(), effective);

            if (!first) {
                builder.append("\n\n");
            }
            builder.append(bloque);
            first = false;

            addedContents.add(effective);
            lastTail = effective;
            lastSourceDoc = chunk.getDocumentId();
            lastSourcePage = chunk.getPageNumber();
            usedChars += effective.length();
            chunksUsados++;
            paginasCubiertasSet.add(chunk.getDocumentId() + ":" + chunk.getPageNumber());
        }

        return new ContextoResult(builder.toString().trim(), chunksUsados, paginasCubiertasSet.size());
    }

    /**
     * Devuelve la longitud (<= 200) de la subcadena compartida entre el final del
     * fragmento previo y el inicio del siguiente, para quitar el solape de indexación.
     */
    private static int detectOverlap(String prev, String next) {
        int max = Math.min(MAX_OVERLAP_TO_STRIP, Math.min(prev.length(), next.length()));
        for (int len = max; len > 0; len--) {
            if (prev.regionMatches(prev.length() - len, next, 0, len)) {
                return len;
            }
        }
        return 0;
    }
}