import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ChatEntry, ChatPanel } from '../chat-panel/chat-panel';
import { DiagramsPanel } from '../diagrams-panel/diagrams-panel';
import { SourcesPanel } from '../sources-panel/sources-panel';
import { RagDocument, TutorRagApiService } from '../tutor-rag-api.service';

type Tab = 'fuentes' | 'chat' | 'diagramas';

const LEARNER_ID_STORAGE_KEY = 'llm-workbench.tutor-rag.learner-id';

/** Contenedor de la sección de prueba del tutor + RAG (EP-05/EP-09), atada al curso igual que
 * `/docente/cursos/:courseId/evaluador/**` — la partición por `courseCohortId` es obligatoria
 * (`AGENTS.md` §2), así que esta sección tampoco queda suelta de un curso. No hay identidad real
 * de alumno en el workbench: se genera un `learnerId` de prueba estable por navegador/origen
 * (misma idea que ya usa `llm-workbench` para golden set en `localStorage`). */
@Component({
  selector: 'app-tutor-rag-shell',
  imports: [SourcesPanel, ChatPanel, DiagramsPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tutor-rag-shell.component.html',
  styleUrl: './tutor-rag-shell.component.scss',
})
export class TutorRagShell {
  private readonly api = inject(TutorRagApiService);

  readonly courseId = input('');
  readonly learnerId = signal(readOrCreateLearnerId());

  readonly activeTab = signal<Tab>('fuentes');
  readonly selectedSourceIds = signal<ReadonlySet<string>>(new Set());
  readonly uploading = signal(false);
  readonly uploadError = signal('');
  readonly entries = signal<ChatEntry[]>([]);
  readonly conversationId = signal<string | null>(null);
  readonly thinking = signal(false);

  readonly sources = httpResource<RagDocument[]>(
    () => (this.courseId() ? `/api/llm/rag/documents?courseCohortId=${encodeURIComponent(this.courseId())}` : undefined),
    { defaultValue: [] },
  );

  readonly hasSelection = computed(() => this.selectedSourceIds().size > 0);
  readonly chatDisabledReason = computed(() => (this.hasSelection() ? '' : 'Marcá al menos una fuente en la pestaña "Fuentes" para poder preguntar.'));

  setTab(tab: Tab): void {
    this.activeTab.set(tab);
  }

  toggleSource(id: string): void {
    const next = new Set(this.selectedSourceIds());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selectedSourceIds.set(next);
  }

  uploadDocument(file: File): void {
    if (!this.courseId()) return;
    this.uploading.set(true);
    this.uploadError.set('');
    this.api.uploadDocument(this.courseId(), file).pipe(
      catchError((error) => {
        this.uploadError.set(error?.error?.detail ?? 'No se pudo subir el PDF. Verificá que sea un archivo válido de hasta 25MB.');
        return of(null);
      }),
    ).subscribe((document) => {
      this.uploading.set(false);
      if (!document) return;
      this.sources.reload();
      this.toggleSource(document.id);
    });
  }

  removeDocument(id: string): void {
    this.api.deleteDocument(id).pipe(catchError(() => of(null))).subscribe(() => {
      this.sources.reload();
      if (this.selectedSourceIds().has(id)) this.toggleSource(id);
    });
  }

  askQuestion(pregunta: string): void {
    const documentIds = [...this.selectedSourceIds()];
    if (!documentIds.length || !this.courseId()) return;

    this.entries.update((current) => [...current, { rol: 'alumno', contenido: pregunta }]);
    this.thinking.set(true);
    this.api.chat(this.courseId(), this.learnerId(), documentIds, pregunta, this.conversationId()).pipe(
      catchError(() => of(null)),
    ).subscribe((response) => {
      this.thinking.set(false);
      if (!response) {
        this.entries.update((current) => [...current, { rol: 'tutor', contenido: 'No se pudo contactar al tutor. Intentá nuevamente.' }]);
        return;
      }
      if (response.conversacionId) this.conversationId.set(response.conversacionId);
      this.entries.update((current) => [...current, { rol: 'tutor', contenido: response.respuesta, fuentes: response.fuentes, estado: response.estado }]);
    });
  }
}

function readOrCreateLearnerId(): string {
  try {
    const stored = sessionStorage.getItem(LEARNER_ID_STORAGE_KEY);
    if (stored) return stored;
    const created = crypto.randomUUID();
    sessionStorage.setItem(LEARNER_ID_STORAGE_KEY, created);
    return created;
  } catch {
    return crypto.randomUUID();
  }
}
