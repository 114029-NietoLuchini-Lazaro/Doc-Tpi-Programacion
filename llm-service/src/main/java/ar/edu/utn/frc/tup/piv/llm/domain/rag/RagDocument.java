package ar.edu.utn.frc.tup.piv.llm.domain.rag;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Una fuente (PDF) subida por un curso/cohorte (EP-09). Portado de
 * `demoLLMSpringAi/.../rag/model/RagDocumentInfo.java`, agregando `courseCohortId` (partición
 * obligatoria, `AGENTS.md` §2) y `active` — retirar una fuente es borrado lógico: deja de usarse
 * en búsquedas pero conserva su historial de auditoría (`docs/epicas/ep-09.md`).
 *
 * <p>Los bytes del PDF NO viven en este record (se cargan aparte, bajo demanda, desde
 * `RagDocumentRepository.getPdfBytes`) para no traer un `BYTEA` potencialmente pesado cada vez que
 * se lista o se consulta un documento. */
public record RagDocument(
    UUID id,
    UUID courseCohortId,
    String fileName,
    long fileSizeBytes,
    int pageCount,
    int chunkCount,
    OffsetDateTime uploadedAt,
    String previewText,
    boolean active) {}
