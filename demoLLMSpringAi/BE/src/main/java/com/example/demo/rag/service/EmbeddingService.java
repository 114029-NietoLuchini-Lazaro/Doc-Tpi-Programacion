package com.example.demo.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper sobre {@link EmbeddingModel} de LangChain4j manteniendo la API pública
 * que consumen el RAG, el controller y el inicializador: vectores float[] con un
 * lote por tamaño fijo, throttling para respetar la free tier de Gemini y reintentos
 * con backoff ante fallas transitorias (429/503).
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int BATCH_SIZE = 16;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INTER_BATCH_SLEEP_MS = 700;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public boolean isAvailable() {
        return embeddingModel != null;
    }

    /**
     * Calcula los vectores densos para una lista de textos mediante lotes.
     * Si un lote falla tras los reintentos se rellena con null para preservar el orden.
     */
    public List<float[]> computeEmbeddings(List<String> texts) {
        if (embeddingModel == null || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> allEmbeddings = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);

            List<float[]> batchVectors = requestWithRetries(batch, 1);
            if (batchVectors != null) {
                allEmbeddings.addAll(batchVectors);
            } else {
                // Rellenar con nulls para mantener el orden si falla
                for (int j = i; j < end; j++) {
                    allEmbeddings.add(null);
                }
            }

            // La free tier de Gemini limita embed_content a 100 requests/min: separar
            // los lotes evita quemar la cuota y mantiene el servicio operativo.
            sleep(INTER_BATCH_SLEEP_MS);
        }

        return allEmbeddings;
    }

    /**
     * Calcula el vector denso para un único texto.
     */
    public float[] computeEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<float[]> list = computeEmbeddings(List.of(text));
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    private List<float[]> requestWithRetries(List<String> batch, int attempt) {
        try {
            List<TextSegment> segments = batch.stream().map(TextSegment::from).toList();
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();

            if (embeddings == null) {
                log.warn("Respuesta sin vectores para lote (intento {}/{}).", attempt, MAX_ATTEMPTS);
                sleep(1000);
                if (attempt < MAX_ATTEMPTS) {
                    return requestWithRetries(batch, attempt + 1);
                }
                return null;
            }

            List<float[]> vectors = new ArrayList<>(embeddings.size());
            for (Embedding embedding : embeddings) {
                vectors.add(embedding.vector());
            }
            return vectors;
        } catch (Exception e) {
            log.warn("Error generando embeddings para lote (intento {}/{}): {}",
                    attempt, MAX_ATTEMPTS, e.getMessage());

            if (attempt < MAX_ATTEMPTS) {
                long waitMs = switch (attempt) {
                    case 1 -> 2_000;
                    case 2 -> 10_000;
                    default -> 30_000;
                };
                log.warn("Reintentando en {} ms...", waitMs);
                sleep(waitMs);
                return requestWithRetries(batch, attempt + 1);
            }
        }

        log.warn("Lote de {} texto(s) sin poder generar embeddings tras {} intentos.",
                batch.size(), MAX_ATTEMPTS);
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}