package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingResult;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DiagramDecodeResult;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DiagramDetectionPort;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DocumentChunk;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.ExtractedPdf;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.ImageDetection;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.PdfTextExtractionPort;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.RagDocument;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.TextChunker;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.VectorStorePort;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.RagDocumentRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Casos de uso de `/api/llm/rag/documents/**` (EP-09, alta/inspección de fuentes): sube y
 * procesa un PDF (extracción de texto, chunking, detección y auto-decodificación de diagramas,
 * embeddings) y lo indexa en {@link VectorStorePort}. Portado de
 * `demoLLMSpringAi/.../controller/RagController.java` (la parte de `processAndIndexPdf`), separando
 * la orquestación (acá) de la extracción/decodificación (puertos hacia `infrastructure`). */
@Service
public class RagIngestionService {
  private static final int PREVIEW_LENGTH = 250;

  private final PdfTextExtractionPort textExtractor;
  private final DiagramDetectionPort diagramDetector;
  private final VectorStorePort vectorStore;
  private final RagDocumentRepository documents;
  private final EmbeddingInvocationService embeddings;
  private final TextChunker chunker = new TextChunker();
  private final long maxUploadBytes;
  private final Duration embeddingTimeout;

  public RagIngestionService(PdfTextExtractionPort textExtractor, DiagramDetectionPort diagramDetector,
      VectorStorePort vectorStore, RagDocumentRepository documents, EmbeddingInvocationService embeddings,
      @Value("${llm.rag.max-upload-bytes:26214400}") long maxUploadBytes,
      @Value("${llm.rag.embedding-timeout-ms:8000}") long embeddingTimeoutMs) {
    this.textExtractor = textExtractor;
    this.diagramDetector = diagramDetector;
    this.vectorStore = vectorStore;
    this.documents = documents;
    this.embeddings = embeddings;
    this.maxUploadBytes = maxUploadBytes;
    this.embeddingTimeout = Duration.ofMillis(embeddingTimeoutMs);
  }

  public RagDocument upload(UUID courseCohortId, String fileName, byte[] bytes) {
    if (courseCohortId == null) {
      throw new IllegalArgumentException("courseCohortId es obligatorio");
    }
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("El archivo PDF está vacío.");
    }
    if (bytes.length > maxUploadBytes) {
      throw new IllegalArgumentException("El archivo supera el tamaño máximo permitido de "
          + (maxUploadBytes / (1024 * 1024)) + "MB.");
    }
    if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
      throw new IllegalArgumentException("Solo se admiten documentos en formato PDF (.pdf).");
    }

    UUID documentId = UUID.randomUUID();
    ExtractedPdf extracted = extractOrFail(bytes);
    List<DocumentChunk> chunks = new ArrayList<>(chunker.createChunks(documentId, fileName, extracted.pages()));

    appendDiagramChunks(documentId, fileName, bytes, chunks);

    String preview = extracted.fullText().length() > PREVIEW_LENGTH
        ? extracted.fullText().substring(0, PREVIEW_LENGTH) + "..."
        : extracted.fullText();

    List<String> chunkTexts = chunks.stream().map(DocumentChunk::content).toList();
    List<EmbeddingResult> chunkEmbeddings = embeddings.embedBatch(chunkTexts, embeddingTimeout);

    RagDocument document = new RagDocument(documentId, courseCohortId, fileName, bytes.length,
        extracted.totalPages(), chunks.size(), OffsetDateTime.now(), preview, true);

    // El documento se guarda primero: rag_chunks.document_id tiene FK contra rag_documents.
    RagDocument saved = documents.save(document, bytes);
    try {
      vectorStore.indexChunks(documentId, chunks, chunkEmbeddings);
    } catch (RuntimeException failure) {
      documents.deactivate(documentId); // no dejar un documento activo sin fragmentos
      throw failure;
    }
    return saved;
  }

  /** `Loader.loadPDF` (dentro de {@link PdfTextExtractionPort}) lanza `InvalidPasswordException`
   * (una `IOException`) para un PDF cifrado con contraseña **antes** de que el adaptador llegue a
   * chequear `document.isEncrypted()` — mismo comportamiento que ya tenía
   * `demoLLMSpringAi/.../PdfTextExtractorService.java`, no es un bug introducido acá. Sin este
   * try/catch esa `IOException` escaparía sin manejar hasta el cliente como `500`, en vez del
   * `422` con mensaje claro que pide la historia (CA3: "archivo cifrado o corrupto se rechaza"). */
  private ExtractedPdf extractOrFail(byte[] bytes) {
    try {
      return textExtractor.extractTextWithPages(bytes);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "No se pudo procesar el PDF: puede estar protegido con contraseña, corrupto o no ser un PDF válido.", exception);
    }
  }

  /** Detecta imágenes/diagramas de contenido genuino y auto-indexa como chunks los que sí se
   * pudieron decodificar (mismo criterio que la demo: descarta los `DESCONOCIDO`). Un fallo acá
   * no aborta la subida del documento — se deja constancia (no se propaga la excepción), igual
   * que `RagController.processAndIndexPdf` en la demo. */
  private void appendDiagramChunks(UUID documentId, String fileName, byte[] bytes, List<DocumentChunk> chunks) {
    try {
      List<ImageDetection> images = diagramDetector.detectImages(bytes);
      for (ImageDetection image : images) {
        DiagramDecodeResult decoded = diagramDetector.decodeDiagram(bytes, image.imageIndex());
        if (!DiagramDecodeResult.TIPO_DESCONOCIDO.equalsIgnoreCase(decoded.tipoDiagrama())) {
          chunks.add(DocumentChunk.nuevo(documentId, fileName, decoded.pageNumber(), chunks.size(), diagramChunkContent(fileName, decoded)));
        }
      }
    } catch (Exception ignored) {
      // Aviso, no aborta la indexación del documento — mismo criterio que la demo.
    }
  }

  public List<RagDocument> list(UUID courseCohortId) {
    return documents.findActiveByCourse(courseCohortId);
  }

  public Optional<RagDocument> get(UUID id) {
    return documents.findById(id);
  }

  public void deactivate(UUID id) {
    documents.findById(id).orElseThrow(() -> new IllegalArgumentException("Fuente no encontrada: " + id));
    documents.deactivate(id);
  }

  public List<DocumentChunk> getChunks(UUID id) {
    return vectorStore.getChunks(id);
  }

  public Optional<byte[]> getPdfBytes(UUID id) {
    return documents.findById(id).map(doc -> documents.getPdfBytes(id));
  }

  public List<ImageDetection> detectImages(byte[] pdfBytes) {
    return diagramDetector.detectImages(pdfBytes);
  }

  public DiagramDecodeResult decodeImage(byte[] pdfBytes, int imageIndex) {
    return diagramDetector.decodeDiagram(pdfBytes, imageIndex);
  }

  /** Persiste manualmente un diagrama ya decodificado (ej. revisado por un docente) como chunk
   * semántico nuevo, calculando su embedding — portado de
   * `RagController.indexDiagramChunk` en la demo. */
  public void indexDiagram(UUID documentId, DiagramDecodeResult result) {
    RagDocument document = documents.findById(documentId)
        .orElseThrow(() -> new IllegalArgumentException("Fuente no encontrada: " + documentId));

    String content = diagramChunkContent(document.fileName(), result);
    DocumentChunk chunk = DocumentChunk.nuevo(documentId, document.fileName(), result.pageNumber(),
        document.chunkCount() + 1, content);
    EmbeddingResult embedding;
    try {
      embedding = embeddings.embed(content, embeddingTimeout);
    } catch (Exception exception) {
      embedding = null; // mismo criterio que la demo: un embedding fallido no bloquea el chunk
    }
    vectorStore.addChunk(chunk, embedding);
  }

  private String diagramChunkContent(String fileName, DiagramDecodeResult result) {
    return String.format(
        "[Fuente: \"%s\" | Pág. %d | Figura/Diagrama: %s]%nTipo: %s%n%nInterpretación:%n%s%n%nEstructura:%n```mermaid%n%s%n```",
        fileName, result.pageNumber(), result.tituloDetectado(), result.tipoDiagrama(),
        result.interpretacion() != null ? result.interpretacion() : "",
        result.mermaidCode() != null ? result.mermaidCode() : "");
  }

  /** Carga y auto-indexa el PDF de demostración PRD en la base de datos para la cohorte indicada.
   * Portado de `RagController.loadSamplePdf` de la demo. */
  public RagDocument uploadSample(UUID courseCohortId) {
    try (var is = getClass().getResourceAsStream("/fuentes/PRD-Plataforma-Gamificada-TP.pdf")) {
      if (is != null) {
        return upload(courseCohortId, "PRD-Plataforma-Gamificada-TP.pdf", is.readAllBytes());
      }
    } catch (IOException ignored) {}

    Path[] candidates = new Path[] {
        Paths.get("src", "main", "resources", "fuentes", "PRD-Plataforma-Gamificada-TP.pdf"),
        Paths.get("docs", "fuentes", "PRD-Plataforma-Gamificada-TP.pdf"),
        Paths.get("llm-service", "docs", "fuentes", "PRD-Plataforma-Gamificada-TP.pdf"),
        Paths.get("..", "docs", "fuentes", "PRD-Plataforma-Gamificada-TP.pdf")
    };
    for (Path path : candidates) {
      if (Files.exists(path)) {
        try {
          byte[] bytes = Files.readAllBytes(path);
          return upload(courseCohortId, "PRD-Plataforma-Gamificada-TP.pdf", bytes);
        } catch (IOException e) {
          throw new IllegalStateException("Error al leer el archivo de muestra: " + path, e);
        }
      }
    }
    throw new IllegalArgumentException("No se encontró el archivo de muestra 'PRD-Plataforma-Gamificada-TP.pdf'.");
  }
}
