import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { RagDocument } from '../tutor-rag-api.service';

/** Panel de fuentes estilo NotebookLM: lista con checkboxes + upload de PDF. Reemplaza
 * `demoLLMSpringAi/FE/src/app/app.html` (panel izquierdo), con signals/inject/OnPush en vez del
 * estado y binding directo (`[(ngModel)]`) de la demo. */
@Component({
  selector: 'app-sources-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sources-panel.component.html',
  styleUrl: './sources-panel.component.scss',
})
export class SourcesPanel {
  readonly sources = input<RagDocument[]>([]);
  readonly selectedIds = input<ReadonlySet<string>>(new Set());
  readonly loading = input(false);
  readonly uploading = input(false);
  readonly uploadError = input('');

  readonly toggle = output<string>();
  readonly upload = output<File>();
  readonly remove = output<string>();

  isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.upload.emit(file);
    }
    input.value = '';
  }

  formatSize(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
