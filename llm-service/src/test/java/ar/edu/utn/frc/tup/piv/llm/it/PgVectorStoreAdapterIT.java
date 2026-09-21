package ar.edu.utn.frc.tup.piv.llm.it;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingResult;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DocumentChunk;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.RagDocument;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.PgVectorStoreAdapter;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.RagDocumentRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Integración real (Postgres + pgvector) del adaptador de búsqueda vectorial de EP-09: valida
 * el operador de distancia coseno `<=>`, el filtrado por documento y el re-indexado. */
class PgVectorStoreAdapterIT extends AbstractIntegrationIT {
  @Autowired PgVectorStoreAdapter vectorStore;
  @Autowired RagDocumentRepository documents;

  private UUID setupDocument() {
    UUID documentId = UUID.randomUUID();
    documents.save(new RagDocument(documentId, UUID.randomUUID(), "test.pdf", 10, 1, 0,
        OffsetDateTime.now(), "preview", true), "pdf".getBytes());
    return documentId;
  }

  private float[] axisVector(int axis) {
    float[] vector = new float[768];
    vector[axis] = 1f;
    return vector;
  }

  private EmbeddingResult embedding(float[] vector) {
    return new EmbeddingResult(vector, "fake", "fake-embedding-768");
  }

  @Test
  void indexChunksStoresTheChunksRetrievableInOrder() {
    UUID documentId = setupDocument();
    DocumentChunk first = DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "primer fragmento");
    DocumentChunk second = DocumentChunk.nuevo(documentId, "test.pdf", 2, 1, "segundo fragmento");

    vectorStore.indexChunks(documentId, List.of(first, second),
        List.of(embedding(axisVector(0)), embedding(axisVector(1))));

    var chunks = vectorStore.getChunks(documentId);
    assertThat(chunks).hasSize(2);
    assertThat(chunks).extracting(DocumentChunk::content).containsExactly("primer fragmento", "segundo fragmento");
  }

  @Test
  void indexChunksReplacesTheChunksOfTheDocument() {
    UUID documentId = setupDocument();
    vectorStore.indexChunks(documentId, List.of(DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "original")),
        List.of(embedding(axisVector(0))));

    vectorStore.indexChunks(documentId, List.of(DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "reemplazo")),
        List.of(embedding(axisVector(0))));

    var chunks = vectorStore.getChunks(documentId);
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).content()).isEqualTo("reemplazo");
  }

  @Test
  void addChunkStoresASingleChunk() {
    UUID documentId = setupDocument();

    vectorStore.addChunk(DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "chunk individual"),
        embedding(axisVector(0)));

    assertThat(vectorStore.getChunks(documentId)).hasSize(1);
  }

  @Test
  void addChunkToleratesANullEmbedding() {
    UUID documentId = setupDocument();

    vectorStore.addChunk(DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "sin vector"), null);

    assertThat(vectorStore.getChunks(documentId)).hasSize(1);
  }

  @Test
  void searchTopKRanksByCosineSimilarity() {
    UUID documentId = setupDocument();
    vectorStore.indexChunks(documentId,
        List.of(DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "cercano"),
            DocumentChunk.nuevo(documentId, "test.pdf", 2, 1, "lejano")),
        List.of(embedding(axisVector(0)), embedding(axisVector(1))));

    var results = vectorStore.searchTopK(List.of(documentId), axisVector(0), 2);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).content()).isEqualTo("cercano");
    assertThat(results.get(0).similarityScore()).isGreaterThan(results.get(1).similarityScore());
  }

  @Test
  void searchTopKRespectsTheLimit() {
    UUID documentId = setupDocument();
    List<DocumentChunk> chunks = new ArrayList<>();
    List<EmbeddingResult> embeddings = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(DocumentChunk.nuevo(documentId, "test.pdf", i + 1, i, "chunk " + i));
      embeddings.add(embedding(axisVector(0)));
    }
    vectorStore.indexChunks(documentId, chunks, embeddings);

    assertThat(vectorStore.searchTopK(List.of(documentId), axisVector(0), 3)).hasSize(3);
  }

  @Test
  void searchTopKOnlySearchesTheRequestedDocuments() {
    UUID documentA = setupDocument();
    UUID documentB = setupDocument();
    vectorStore.indexChunks(documentA, List.of(DocumentChunk.nuevo(documentA, "a.pdf", 1, 0, "contenido A")),
        List.of(embedding(axisVector(0))));
    vectorStore.indexChunks(documentB, List.of(DocumentChunk.nuevo(documentB, "b.pdf", 1, 0, "contenido B")),
        List.of(embedding(axisVector(0))));

    var results = vectorStore.searchTopK(List.of(documentA), axisVector(0), 5);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).documentId()).isEqualTo(documentA);
  }

  @Test
  void searchTopKWithNoDocumentsReturnsEmpty() {
    assertThat(vectorStore.searchTopK(List.of(), axisVector(0), 5)).isEmpty();
  }

  @Test
  void searchTopKWithANullVectorReturnsEmpty() {
    assertThat(vectorStore.searchTopK(List.of(UUID.randomUUID()), null, 5)).isEmpty();
  }

  @Test
  void deleteChunksRemovesEveryChunkOfTheDocument() {
    UUID documentId = setupDocument();
    vectorStore.indexChunks(documentId,
        List.of(DocumentChunk.nuevo(documentId, "test.pdf", 1, 0, "a"),
            DocumentChunk.nuevo(documentId, "test.pdf", 2, 1, "b")),
        List.of(embedding(axisVector(0)), embedding(axisVector(1))));

    vectorStore.deleteChunks(documentId);

    assertThat(vectorStore.getChunks(documentId)).isEmpty();
  }
}
