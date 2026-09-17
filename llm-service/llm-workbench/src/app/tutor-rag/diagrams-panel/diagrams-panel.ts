import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { DiagramDecodeResult, ImageDetection, RagDocument, TutorRagApiService } from '../tutor-rag-api.service';

/** Panel de inspección de imágenes/diagramas de un PDF, decodificados de forma determinística
 * (sin IA) por `PdfDiagramDetectionAdapter`. No es el pipeline de visión (OpenCV/Tess4J) del
 * spike `docs/31-spike-...md` — es la heurística de bounding boxes ya existente en la demo. */
@Component({
  selector: 'app-diagrams-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './diagrams-panel.component.html',
  styleUrl: './diagrams-panel.component.scss',
})
export class DiagramsPanel {
  private readonly api = inject(TutorRagApiService);

  readonly sources = input<RagDocument[]>([]);

  readonly selectedDocId = signal<string | null>(null);
  readonly decoded = signal<Map<number, DiagramDecodeResult>>(new Map());
  readonly decodingIndex = signal<number | null>(null);
  readonly indexError = signal('');

  readonly images = httpResource<ImageDetection[]>(
    () => (this.selectedDocId() ? `/api/llm/rag/documents/${this.selectedDocId()}/images` : undefined),
    { defaultValue: [] },
  );

  readonly selectedDocName = computed(() => this.sources().find((s) => s.id === this.selectedDocId())?.fileName ?? '');

  selectDocument(id: string): void {
    this.selectedDocId.set(id || null);
    this.decoded.set(new Map());
    this.indexError.set('');
  }

  decode(imageIndex: number): void {
    const docId = this.selectedDocId();
    if (!docId) return;
    this.decodingIndex.set(imageIndex);
    this.indexError.set('');
    this.api.decodeImage(docId, imageIndex).pipe(
      catchError(() => { this.indexError.set('No se pudo decodificar la imagen.'); return of(null); }),
    ).subscribe((result) => {
      this.decodingIndex.set(null);
      if (!result) return;
      const next = new Map(this.decoded());
      next.set(imageIndex, result);
      this.decoded.set(next);
    });
  }

  indexAsChunk(imageIndex: number): void {
    const docId = this.selectedDocId();
    const result = this.decoded().get(imageIndex);
    if (!docId || !result) return;
    this.api.indexDiagram(docId, result).pipe(
      catchError(() => { this.indexError.set('No se pudo indexar el diagrama como chunk.'); return of(null); }),
    ).subscribe();
  }

  isKnownDiagram(result: DiagramDecodeResult): boolean {
    return result.tipoDiagrama !== 'DESCONOCIDO';
  }
}
