package com.example.demo.config;

import com.example.demo.rag.service.EmbeddingService;
import com.example.demo.rag.service.PgVectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Rehidrata el espejo en memoria del RAG desde pgvector al arrancar y re-embebe
 * los chunks que quedaron con embedding NULL (indexaciones hechas sin credenciales).
 * Sin esto, tras un reinicio el chat queda sin contexto aunque los datos sigan en PostgreSQL.
 */
@Component
public class RagIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(RagIndexInitializer.class);

    private final PgVectorStoreService vectorStore;
    private final EmbeddingService embeddingService;

    public RagIndexInitializer(PgVectorStoreService vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        vectorStore.rehydrateFromPostgres();

        int withoutEmbeddings = vectorStore.countChunksWithoutEmbeddings();
        if (withoutEmbeddings > 0) {
            log.info("Hay {} chunk(s) sin embedding en pgvector. Intentando re-embedding...", withoutEmbeddings);
            int repaired = vectorStore.reembedMissingChunks(embeddingService);
            if (repaired > 0) {
                vectorStore.rehydrateFromPostgres();
            }
        }
    }
}