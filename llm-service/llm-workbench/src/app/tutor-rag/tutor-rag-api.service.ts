import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { createIdempotencyKey } from '../course-api.service';

/** Espejo de `RagDocument` (`docs/contracts/llm-service-v1.openapi.yaml`). */
export interface RagDocument {
  id: string;
  courseCohortId: string;
  fileName: string;
  fileSizeBytes: number;
  pageCount: number;
  chunkCount: number;
  uploadedAt: string;
  previewText: string | null;
  active: boolean;
}

/** Espejo de `DocumentChunk`. */
export interface DocumentChunk {
  id: string;
  documentId: string;
  documentName: string;
  pageNumber: number;
  chunkIndex: number;
  content: string;
  similarityScore: number;
}

/** Espejo de `ImageDetection`. */
export interface ImageDetection {
  imageIndex: number;
  pageNumber: number;
  width: number;
  height: number;
  format: string;
  base64Data: string;
  titleHint: string | null;
}

/** Espejo de `DiagramDecodeResult`. */
export interface DiagramDecodeResult {
  imageIndex: number;
  pageNumber: number;
  tituloDetectado: string;
  tipoDiagrama: string;
  interpretacion: string;
  mermaidCode: string;
  elementosEncontrados: string[];
}

/** Espejo de `RagSourceCitation`. */
export interface RagSourceCitation {
  documentId: string;
  documentName: string;
  pageNumber: number;
  chunkIndex: number;
  score: number;
  textoExtracto: string;
}

/** Espejo de `RagChatResponse`. */
export interface RagChatResponse {
  respuesta: string;
  estado: string;
  mensajeValidacion: string | null;
  tokensGastados: number;
  cached: boolean;
  rolTutor: string;
  fuentes: RagSourceCitation[];
  conversacionId: string | null;
}

/** Espejo de `Conversation`. */
export interface Conversation {
  id: string;
  courseCohortId: string;
  learnerId: string;
  challengeId: string | null;
  titulo: string;
  estado: string;
  createdAt: string;
}

/** Espejo de `Message`. */
export interface Message {
  id: string;
  conversationId: string;
  rol: 'alumno' | 'tutor';
  contenido: string;
  timestamp: string;
}

/** Cliente Angular para `/api/llm/rag/**` y `/api/llm/tutor/conversations/**`
 * (`docs/contracts/llm-service-v1.openapi.yaml`, EP-09). Rutas siempre relativas: el proxy
 * (`proxy.local.json`/`proxy.workbench.json`) resuelve el host real, nunca se hardcodea acá —
 * a diferencia de `demoLLMSpringAi/FE/src/app/services/rag.service.ts`, que sí hardcodeaba
 * `http://localhost:8080`. */
@Injectable({ providedIn: 'root' })
export class TutorRagApiService {
  private readonly http = inject(HttpClient);

  listDocuments(courseCohortId: string) {
    return this.http.get<RagDocument[]>('/api/llm/rag/documents', { params: { courseCohortId } });
  }

  uploadDocument(courseCohortId: string, file: File) {
    const form = new FormData();
    form.append('courseCohortId', courseCohortId);
    form.append('file', file);
    return this.http.post<RagDocument>('/api/llm/rag/documents', form, { headers: { 'Idempotency-Key': createIdempotencyKey() } });
  }

  uploadSampleDocument(courseCohortId: string) {
    return this.http.post<RagDocument>(
      '/api/llm/rag/documents/sample',
      null,
      {
        params: { courseCohortId },
        headers: { 'Idempotency-Key': createIdempotencyKey() }
      }
    );
  }

  deleteDocument(id: string) {
    return this.http.delete<void>(`/api/llm/rag/documents/${id}`);
  }

  documentChunks(id: string) {
    return this.http.get<DocumentChunk[]>(`/api/llm/rag/documents/${id}/chunks`);
  }

  documentImages(id: string) {
    return this.http.get<ImageDetection[]>(`/api/llm/rag/documents/${id}/images`);
  }

  decodeImage(id: string, imageIndex: number) {
    return this.http.post<DiagramDecodeResult>(`/api/llm/rag/documents/${id}/images/${imageIndex}/decode`, {});
  }

  indexDiagram(id: string, result: DiagramDecodeResult) {
    return this.http.post<void>(`/api/llm/rag/documents/${id}/diagrams`, result);
  }

  chat(courseCohortId: string, learnerId: string, documentIds: string[], pregunta: string, conversacionId: string | null) {
    return this.http.post<RagChatResponse>('/api/llm/rag/chat',
      { courseCohortId, learnerId, documentIds, pregunta, conversacionId },
      { headers: { 'Idempotency-Key': createIdempotencyKey() } });
  }

  createConversation(courseCohortId: string, learnerId: string, titulo: string) {
    return this.http.post<Conversation>('/api/llm/tutor/conversations',
      { courseCohortId, learnerId, titulo }, { headers: { 'Idempotency-Key': createIdempotencyKey() } });
  }

  conversationMessages(id: string) {
    return this.http.get<Message[]>(`/api/llm/tutor/conversations/${id}/messages`);
  }
}
