package com.example.demo.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentInfo {
    private String documentId;
    private String fileName;
    private long fileSizeBytes;
    private int pageCount;
    private int chunkCount;
    private LocalDateTime uploadedAt;
    private String previewText;

    /** Páginas del PDF que tenían texto extraíble (cobertura real de la indexación). */
    @Builder.Default
    private int pageCountConTexto = 0;

    /** true si todos los chunks quedaron con embedding pgvector (búsqueda semántica disponible). */
    @Builder.Default
    private boolean indexedWithVectors = true;
}
