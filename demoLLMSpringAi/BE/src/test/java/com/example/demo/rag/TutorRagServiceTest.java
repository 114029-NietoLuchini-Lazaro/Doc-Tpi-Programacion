package com.example.demo.rag;

import com.example.demo.rag.dto.RagChatRequest;
import com.example.demo.rag.dto.RagChatResponse;
import com.example.demo.rag.service.EmbeddingService;
import com.example.demo.rag.service.PgVectorStoreService;
import com.example.demo.rag.service.RagContextService;
import com.example.demo.rag.service.TutorRagService;
import com.example.demo.repository.ConversacionRepository;
import com.example.demo.repository.MensajeRepository;
import com.example.demo.security.GuardrailService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TutorRagServiceTest {

    private TutorRagService tutorRagService;
    private PgVectorStoreService vectorStore;
    private EmbeddingService embeddingService;
    private GuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        vectorStore = mock(PgVectorStoreService.class);
        embeddingService = mock(EmbeddingService.class);
        guardrailService = mock(GuardrailService.class);
        ConversacionRepository conversacionRepository = mock(ConversacionRepository.class);
        MensajeRepository mensajeRepository = mock(MensajeRepository.class);

        tutorRagService = new TutorRagService(
                chatModel,
                vectorStore,
                embeddingService,
                guardrailService,
                new RagContextService(),
                conversacionRepository,
                mensajeRepository
        );
    }

    @Test
    void testRetrievalEmptyDevuelveBlockedNoContextSinLlamarAlLlm() {
        RagChatRequest request = new RagChatRequest("doc1", List.of("doc1"), "¿qué es la recursividad?", null);

        when(guardrailService.validateQuery(anyString(), anyString(), anyString()))
                .thenReturn(new GuardrailService.ValidationResult(true, "OK", ""));
        when(guardrailService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(vectorStore.searchTopKMultiDocTracked(any(), any(), anyString(), anyInt()))
                .thenReturn(new PgVectorStoreService.RetrievalResult(List.of(), false));
        when(embeddingService.isAvailable()).thenReturn(true);

        RagChatResponse response = tutorRagService.responderConsultaRag(request, "127.0.0.1");

        assertEquals("BLOCKED_NO_CONTEXT", response.getEstado());
        assertEquals(0, response.getTokensGastados());
        assertEquals(0, response.getNroChunks());
        assertEquals(0, response.getPaginasCubiertas());
        assertEquals("MEMORIA", response.getRutaRetrieval());
        assertTrue(response.getFuentes().isEmpty());
        verify(vectorStore, never()).searchTopKMultiDoc(any(), any(), anyString(), anyInt());
    }
}