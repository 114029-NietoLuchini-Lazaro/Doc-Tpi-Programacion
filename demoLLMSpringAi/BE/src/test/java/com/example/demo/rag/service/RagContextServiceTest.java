package com.example.demo.rag.service;

import com.example.demo.rag.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagContextServiceTest {

    private final RagContextService contextService = new RagContextService();

    private DocumentChunk chunk(String id, String docId, String docName, int page, int index, String content) {
        return DocumentChunk.builder()
                .id(id)
                .documentId(docId)
                .documentName(docName)
                .pageNumber(page)
                .chunkIndex(index)
                .content(content)
                .build();
    }

    @Test
    void ensamblaEnOrdenEstablePorDocumentoPaginaEIndice() {
        List<DocumentChunk> desordenados = List.of(
                chunk("b", "doc2", "Dos", 1, 0, "contenido de la segunda fuente"),
                chunk("c", "doc1", "Uno", 2, 1, "pagina dos bis"),
                chunk("a", "doc1", "Uno", 1, 0, "primera pagina")
        );

        RagContextService.ContextoResult result = contextService.ensamblarContexto(desordenados);

        int p1 = result.texto().indexOf("primera pagina");
        int p2 = result.texto().indexOf("pagina dos bis");
        int p3 = result.texto().indexOf("segunda fuente");
        assertTrue(p1 < p2 && p2 < p3, "El contexto debe respetar (doc, página, índice): " + result.texto());
        assertEquals(3, result.nroChunksUsados());
        assertEquals(3, result.paginasCubiertas());
    }

    @Test
    void eliminaElSolapeEntreFragmentosConsecutivosDeLaMismaFuente() {
        List<DocumentChunk> chunks = List.of(
                chunk("a", "doc1", "Uno", 1, 0, "ABCDEFG"),
                chunk("b", "doc1", "Uno", 1, 1, "FGHIJ")
        );

        RagContextService.ContextoResult result = contextService.ensamblarContexto(chunks);

        assertTrue(result.texto().contains("ABCDEFG"), result.texto());
        assertTrue(result.texto().contains("HIJ"), "El solape FG debe quitarse antes de append: " + result.texto());
        assertFalse(result.texto().contains("EFGFGHIJ"), "El solape no debe duplicarse: " + result.texto());
    }

    @Test
    void respetaElPresupuestoDeCaracteres() {
        List<DocumentChunk> chunks = List.of(
                chunk("a", "doc1", "Uno", 1, 0, "primer fragmento de la pagina"),
                chunk("b", "doc1", "Uno", 1, 1, "segundo fragmento de la pagina"),
                chunk("c", "doc1", "Uno", 1, 2, "tercer fragmento de la pagina")
        );

        RagContextService.ContextoResult result = contextService.ensamblarContexto(chunks, 40);

        assertEquals(1, result.nroChunksUsados(), "Con presupuesto corto debe entrar solo el primer fragmento");
        assertTrue(result.texto().length() <= 120, "Un solo fragmento entra si es el primero");
    }

    @Test
    void unaFuenteVaciaOConContenidoNuloNoRompeElEnsamblado() {
        RagContextService.ContextoResult empty = contextService.ensamblarContexto(List.of());
        assertEquals(0, empty.nroChunksUsados());
        assertEquals("", empty.texto());

        RagContextService.ContextoResult conNulos = contextService.ensamblarContexto(List.of(
                chunk("a", "doc1", "Uno", 1, 0, null),
                chunk("b", "doc1", "Uno", 2, 0, null)));
        assertEquals(0, conNulos.nroChunksUsados());
        assertEquals("", conNulos.texto());
    }
}