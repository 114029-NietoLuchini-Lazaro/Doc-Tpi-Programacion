package com.example.demo.rag.service;

import com.example.demo.rag.model.DocumentChunk;
import com.example.demo.rag.model.RagDocumentInfo;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PgVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreService.class);

    private final JdbcTemplate jdbcTemplate;
    private final InMemoryRagVectorStore fallbackMemoryStore;
    private final boolean isPostgres;

    public PgVectorStoreService(JdbcTemplate jdbcTemplate,
                                DataSource dataSource,
                                InMemoryRagVectorStore fallbackMemoryStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.fallbackMemoryStore = fallbackMemoryStore;
        this.isPostgres = checkIsPostgres(dataSource);

        if (this.isPostgres) {
            log.info("🐘 PgVectorStoreService inicializado con PostgreSQL / pgvector (Nube o local).");
            ensureTablesAndExtension();
        } else {
            log.info("💾 Base de datos no-Postgres detectada. PgVectorStoreService operará en modo memoria sincronizada.");
        }
    }

    private boolean checkIsPostgres(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            return prod != null && prod.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            log.warn("No se pudo determinar el tipo de base de datos: {}", e.getMessage());
            return false;
        }
    }

    private void ensureTablesAndExtension() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector;");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_documentos (
                    document_id VARCHAR(64) PRIMARY KEY,
                    file_name VARCHAR(255) NOT NULL,
                    file_size_bytes BIGINT NOT NULL,
                    page_count INT NOT NULL,
                    chunk_count INT NOT NULL,
                    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    preview_text TEXT
                );
            """);
            jdbcTemplate.execute("ALTER TABLE rag_documentos ADD COLUMN IF NOT EXISTS pdf_bytes BYTEA;");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_chunks (
                    id VARCHAR(64) PRIMARY KEY,
                    document_id VARCHAR(64) NOT NULL REFERENCES rag_documentos(document_id) ON DELETE CASCADE,
                    document_name VARCHAR(255) NOT NULL,
                    page_number INT NOT NULL,
                    chunk_index INT NOT NULL,
                    content TEXT NOT NULL,
                    embedding vector(3072)
                );
            """);
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_rag_chunks_hnsw;");
            jdbcTemplate.execute("ALTER TABLE rag_chunks ALTER COLUMN embedding TYPE vector(3072) USING embedding::vector;");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rag_chunks_document_id ON rag_chunks(document_id);");
            log.info("Tablas de RAG y extensión vector verificadas exitosamente en PostgreSQL.");
        } catch (Exception e) {
            log.warn("Aviso al verificar extensión/tablas en PostgreSQL: {}", e.getMessage());
        }
    }

    public boolean isPostgresActive() {
        return isPostgres;
    }

    /**
     * Recarga el espejo en memoria desde pgvector. El espejo es la única fuente del
     * fallback TF-IDF del chat; si el proceso se reinicia y no se rehidrata, el chat
     * queda sin contexto a pesar de que los datos siguen en PostgreSQL.
     */
    public void rehydrateFromPostgres() {
        if (!isPostgres) {
            log.warn("💾 Modo memoria: el RAG no tiene pgvector. Sin rehidratación desde PostgreSQL.");
            return;
        }
        try {
            List<RagDocumentInfo> docs = getAllDocuments();
            int totalChunks = 0;
            int documentsLoaded = 0;
            for (RagDocumentInfo doc : docs) {
                List<DocumentChunk> chunks = getAllChunks(doc.getDocumentId());
                if (chunks == null || chunks.isEmpty()) {
                    continue;
                }
                try {
                    doc.setChunkCount(chunks.size());
                    fallbackMemoryStore.indexDocument(doc, chunks);
                    documentsLoaded++;
                    totalChunks += chunks.size();
                } catch (Exception e) {
                    log.warn("No se pudo rehidratar el documento {}: {}", doc.getDocumentId(), e.getMessage());
                }
            }
            log.info("🐘 Espejo en memoria rehidratado desde pgvector: {} documento(s), {} chunks.", documentsLoaded, totalChunks);
        } catch (Exception e) {
            log.warn("Error rehidratando el espejo en memoria desde PostgreSQL: {}", e.getMessage());
        }
    }

    /**
     * Cuenta cuántos chunks de pgvector no tienen embedding (indexación hecha sin
     * servicio de embeddings disponible).
     */
    public int countChunksWithoutEmbeddings() {
        if (!isPostgres) {
            return 0;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM rag_chunks WHERE embedding IS NULL",
                    Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Error contando chunks sin embedding: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Re-calcula el embedding de los chunks que quedaron con NULL. Solo corre si el
     * servicio de embeddings está disponible. Devuelve la cantidad de chunks reparados.
     */
    public int reembedMissingChunks(EmbeddingService embeddingService) {
        if (!isPostgres || embeddingService == null || !embeddingService.isAvailable()) {
            log.info("Re-embedding de chunks omitido (pgvector inactivo o servicio de embeddings no disponible).");
            return 0;
        }
        try {
            List<ChunkContentRow> missing = jdbcTemplate.query(
                    "SELECT id, content FROM rag_chunks WHERE embedding IS NULL ORDER BY document_id, chunk_index",
                    (rs, rowNum) -> new ChunkContentRow(rs.getString("id"), rs.getString("content")));

            if (missing.isEmpty()) {
                log.info("No hay chunks sin embedding que reparar.");
                return 0;
            }

            List<String> contents = missing.stream().map(ChunkContentRow::content).toList();
            List<float[]> embeddings = embeddingService.computeEmbeddings(contents);

            int repaired = 0;
            for (int i = 0; i < missing.size(); i++) {
                float[] emb = (embeddings != null && i < embeddings.size()) ? embeddings.get(i) : null;
                if (emb == null) {
                    continue;
                }
                PGvector vector = new PGvector(emb);
                int updated = jdbcTemplate.update("UPDATE rag_chunks SET embedding = ? WHERE id = ?", vector, missing.get(i).id());
                repaired += updated;
            }
            log.info("Re-embedding finalizado: {} de {} chunks reparados con vector.", repaired, missing.size());
            return repaired;
        } catch (Exception e) {
            log.warn("Error al re-embeder chunks NULL: {}", e.getMessage());
            return 0;
        }
    }

    private record ChunkContentRow(String id, String content) {}

    /**
     * Indexa un documento y sus chunks con vectores densos en pgvector.
     */
    public void indexDocumentWithVectors(RagDocumentInfo doc, List<DocumentChunk> chunks, List<float[]> embeddings) {
        indexDocumentWithVectors(doc, chunks, embeddings, null);
    }

    /**
     * Indexa un documento con sus chunks y persiste los bytes del PDF en la base de datos en la nube.
     */
    public void indexDocumentWithVectors(RagDocumentInfo doc, List<DocumentChunk> chunks, List<float[]> embeddings, byte[] pdfBytes) {
        // Guardar siempre en memoria como espejo
        fallbackMemoryStore.indexDocument(doc, chunks);

        if (!isPostgres) {
            return;
        }

        try {
            // 1. Guardar o actualizar documento incluyendo los bytes originales del PDF
            String sqlDoc = """
                INSERT INTO rag_documentos (document_id, file_name, file_size_bytes, page_count, chunk_count, uploaded_at, preview_text, pdf_bytes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (document_id) DO UPDATE SET
                    file_name = EXCLUDED.file_name,
                    file_size_bytes = EXCLUDED.file_size_bytes,
                    page_count = EXCLUDED.page_count,
                    chunk_count = EXCLUDED.chunk_count,
                    preview_text = EXCLUDED.preview_text,
                    pdf_bytes = COALESCE(EXCLUDED.pdf_bytes, rag_documentos.pdf_bytes);
            """;

            jdbcTemplate.update(sqlDoc,
                    doc.getDocumentId(),
                    doc.getFileName(),
                    doc.getFileSizeBytes(),
                    doc.getPageCount(),
                    doc.getChunkCount(),
                    Timestamp.valueOf(doc.getUploadedAt() != null ? doc.getUploadedAt() : LocalDateTime.now()),
                    doc.getPreviewText(),
                    pdfBytes
            );

            // 2. Eliminar chunks previos si existieran
            jdbcTemplate.update("DELETE FROM rag_chunks WHERE document_id = ?", doc.getDocumentId());

            // 3. Insertar chunks con sus vectores pgvector
            String sqlChunk = """
                INSERT INTO rag_chunks (id, document_id, document_name, page_number, chunk_index, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

            List<Object[]> batchArgs = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                float[] emb = (embeddings != null && i < embeddings.size()) ? embeddings.get(i) : null;
                PGvector pgVector = (emb != null) ? new PGvector(emb) : null;

                batchArgs.add(new Object[]{
                        chunk.getId(),
                        doc.getDocumentId(),
                        doc.getFileName(),
                        chunk.getPageNumber(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        pgVector
                });
            }

            jdbcTemplate.batchUpdate(sqlChunk, batchArgs);
            log.info("✅ {} chunks indexados en pgvector para documento: {}", chunks.size(), doc.getFileName());

        } catch (Exception e) {
            log.error("Error al persistir chunks en pgvector: {}", e.getMessage(), e);
        }
    }

    /**
     * Agrega un chunk individual (ej. diagrama decodificado) a la memoria y a pgvector.
     */
    public void addSingleChunkWithVector(DocumentChunk chunk, float[] embedding) {
        fallbackMemoryStore.addSingleChunk(chunk);

        if (!isPostgres) {
            return;
        }

        try {
            String sqlChunk = """
                INSERT INTO rag_chunks (id, document_id, document_name, page_number, chunk_index, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            PGvector pgVector = (embedding != null) ? new PGvector(embedding) : null;
            jdbcTemplate.update(sqlChunk,
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getDocumentName(),
                    chunk.getPageNumber(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    pgVector
            );
            log.info("✅ Chunk individual (diagrama) persistido en pgvector para: {}", chunk.getDocumentName());
        } catch (Exception e) {
            log.error("Error al persistir chunk individual en pgvector: {}", e.getMessage(), e);
        }
    }

    /**
     * Búsqueda semántica cruzada multi-documento estilo NotebookLM.
     * Filtra estrictamente por los documentIds seleccionados.
     */
    public List<DocumentChunk> searchTopKMultiDoc(List<String> documentIds, float[] queryVector, String queryText, int topK) {
        return searchTopKMultiDocTracked(documentIds, queryVector, queryText, topK).chunks();
    }

    /**
     * Resultado de una búsqueda multi-documento con trazabilidad de la ruta usada.
     */
    public record RetrievalResult(List<DocumentChunk> chunks, boolean usedPgvector) {}

    /**
     * Búsqueda multi-documento con routing explícito: pgvector cuando hay embeddings
     * disponibles, o fallback TF-IDF en memoria. Expone qué ruta se usó para observabilidad.
     */
    public RetrievalResult searchTopKMultiDocTracked(List<String> documentIds, float[] queryVector, String queryText, int topK) {
        if (documentIds == null || documentIds.isEmpty()) {
            return new RetrievalResult(Collections.emptyList(), false);
        }

        // Si tenemos pgvector y vector de consulta, ejecutamos la búsqueda en PostgreSQL
        if (isPostgres && queryVector != null) {
            try {
                PGvector pgQueryVector = new PGvector(queryVector);
                String inSql = String.join(",", Collections.nCopies(documentIds.size(), "?"));

                String sql = String.format("""
                    SELECT id, document_id, document_name, page_number, chunk_index, content,
                           (1 - (embedding <=> ?)) AS similarity
                    FROM rag_chunks
                    WHERE document_id IN (%s)
                      AND embedding IS NOT NULL
                    ORDER BY embedding <=> ?
                    LIMIT ?
                """, inSql);

                List<Object> params = new ArrayList<>();
                params.add(pgQueryVector);
                params.addAll(documentIds);
                params.add(pgQueryVector);
                params.add(topK);

                List<DocumentChunk> results = jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) ->
                        DocumentChunk.builder()
                                .id(rs.getString("id"))
                                .documentId(rs.getString("document_id"))
                                .documentName(rs.getString("document_name"))
                                .pageNumber(rs.getInt("page_number"))
                                .chunkIndex(rs.getInt("chunk_index"))
                                .content(rs.getString("content"))
                                .similarityScore(rs.getDouble("similarity"))
                                .build()
                );

                if (!results.isEmpty()) {
                    return new RetrievalResult(results, true);
                }
                log.warn("Búsqueda vectorial sin resultados para docIds={} (posibles embeddings NULL al indexar). Recurriendo a fallback en memoria.", documentIds);
            } catch (Exception e) {
                log.warn("Fallo búsqueda vectorial en pgvector, recurriendo a fallback en memoria: {}", e.getMessage());
            }
        }

        // Fallback en memoria multi-documento (TF-IDF cross-retrieval)
        return new RetrievalResult(searchInMemoryMultiDoc(documentIds, queryText, topK), false);
    }

    private List<DocumentChunk> searchInMemoryMultiDoc(List<String> documentIds, String queryText, int topK) {
        List<DocumentChunk> combined = new ArrayList<>();
        for (String docId : documentIds) {
            combined.addAll(fallbackMemoryStore.searchTopK(docId, queryText, topK));
        }
        // Ordenar por similitud y tomar los topK globales
        return combined.stream()
                .sorted(Comparator.comparingDouble(DocumentChunk::getSimilarityScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Lista todos los documentos registrados.
     */
    public List<RagDocumentInfo> getAllDocuments() {
        if (isPostgres) {
            try {
                String sql = """
                    SELECT d.document_id, d.file_name, d.file_size_bytes, d.page_count, d.chunk_count,
                           d.uploaded_at, d.preview_text,
                           (SELECT COUNT(DISTINCT c.page_number) FROM rag_chunks c WHERE c.document_id = d.document_id) AS page_count_con_texto,
                           (SELECT COUNT(1) FROM rag_chunks c WHERE c.document_id = d.document_id AND c.embedding IS NOT NULL) AS chunks_con_vector
                    FROM rag_documentos d
                    ORDER BY d.uploaded_at DESC
                """;
                return jdbcTemplate.query(sql, (rs, rowNum) -> mapDocumentRow(rs));
            } catch (Exception e) {
                log.warn("Error leyendo documentos de PostgreSQL: {}", e.getMessage());
            }
        }
        return fallbackMemoryStore.getAllDocuments();
    }

    public Optional<RagDocumentInfo> getDocument(String documentId) {
        if (isPostgres) {
            try {
                String sql = """
                    SELECT d.document_id, d.file_name, d.file_size_bytes, d.page_count, d.chunk_count,
                           d.uploaded_at, d.preview_text,
                           (SELECT COUNT(DISTINCT c.page_number) FROM rag_chunks c WHERE c.document_id = d.document_id) AS page_count_con_texto,
                           (SELECT COUNT(1) FROM rag_chunks c WHERE c.document_id = d.document_id AND c.embedding IS NOT NULL) AS chunks_con_vector
                    FROM rag_documentos d
                    WHERE d.document_id = ?
                """;
                List<RagDocumentInfo> list = jdbcTemplate.query(sql, new Object[]{documentId}, (rs, rowNum) -> mapDocumentRow(rs));
                if (!list.isEmpty()) {
                    return Optional.of(list.get(0));
                }
            } catch (Exception e) {
                log.warn("Error leyendo documento de PostgreSQL: {}", e.getMessage());
            }
        }
        return fallbackMemoryStore.getDocument(documentId);
    }

    private RagDocumentInfo mapDocumentRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp("uploaded_at");
        LocalDateTime dt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        int chunksConVector = rs.getInt("chunks_con_vector");
        int pageCountConTexto = rs.getInt("page_count_con_texto");
        return RagDocumentInfo.builder()
                .documentId(rs.getString("document_id"))
                .fileName(rs.getString("file_name"))
                .fileSizeBytes(rs.getLong("file_size_bytes"))
                .pageCount(rs.getInt("page_count"))
                .chunkCount(rs.getInt("chunk_count"))
                .uploadedAt(dt)
                .previewText(rs.getString("preview_text"))
                .pageCountConTexto(pageCountConTexto)
                .indexedWithVectors(chunksConVector > 0)
                .build();
    }

    public List<DocumentChunk> getAllChunks(String documentId) {
        if (isPostgres) {
            try {
                String sql = """
                    SELECT id, document_id, document_name, page_number, chunk_index, content
                    FROM rag_chunks
                    WHERE document_id = ?
                    ORDER BY chunk_index ASC
                """;
                return jdbcTemplate.query(sql, new Object[]{documentId}, (rs, rowNum) ->
                        DocumentChunk.builder()
                                .id(rs.getString("id"))
                                .documentId(rs.getString("document_id"))
                                .documentName(rs.getString("document_name"))
                                .pageNumber(rs.getInt("page_number"))
                                .chunkIndex(rs.getInt("chunk_index"))
                                .content(rs.getString("content"))
                                .build()
                );
            } catch (Exception e) {
                log.warn("Error leyendo chunks de PostgreSQL: {}", e.getMessage());
            }
        }
        return fallbackMemoryStore.getAllChunks(documentId);
    }

    /**
     * Elimina un documento y todos sus fragmentos.
     */
    public void deleteDocument(String documentId) {
        fallbackMemoryStore.removeDocument(documentId);
        if (isPostgres) {
            try {
                jdbcTemplate.update("DELETE FROM rag_documentos WHERE document_id = ?", documentId);
                log.info("Documento {} eliminado de PostgreSQL.", documentId);
            } catch (Exception e) {
                log.error("Error al eliminar documento de PostgreSQL: {}", e.getMessage());
            }
        }
    }

    /**
     * Recupera los bytes crudos del PDF almacenados en la base de datos de Supabase.
     */
    public byte[] getDocumentPdfBytes(String documentId) {
        if (!isPostgres || documentId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT pdf_bytes FROM rag_documentos WHERE document_id = ?",
                    byte[].class,
                    documentId
            );
        } catch (Exception e) {
            log.debug("No se encontraron pdf_bytes en BD para {}: {}", documentId, e.getMessage());
            return null;
        }
    }
}
