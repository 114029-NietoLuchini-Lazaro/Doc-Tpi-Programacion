import { ChangeDetectionStrategy, Component, effect, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { RagSourceCitation } from '../tutor-rag-api.service';

export interface ChatEntry {
  rol: 'alumno' | 'tutor';
  contenido: string;
  fuentes?: RagSourceCitation[];
  estado?: string;
}

/** Panel de chat: mensajes + input + citas expandibles. Reemplaza el panel derecho de
 * `demoLLMSpringAi/FE/src/app/app.html`, con `ReactiveFormsModule` en vez de `[(ngModel)]`
 * (regla de `AGENTS.md` — la demo usaba `FormsModule` con binding directo). */
@Component({
  selector: 'app-chat-panel',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-panel.component.html',
  styleUrl: './chat-panel.component.scss',
})
export class ChatPanel {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly entries = input<ChatEntry[]>([]);
  readonly thinking = input(false);
  readonly disabled = input(false);
  readonly disabledReason = input('');

  readonly send = output<string>();

  readonly control = this.formBuilder.control('', [Validators.required, Validators.minLength(4), Validators.maxLength(600)]);

  constructor() {
    effect(() => {
      if (this.disabled()) this.control.disable(); else this.control.enable();
    });
  }

  submit(): void {
    const pregunta = this.control.value.trim();
    if (!pregunta || this.control.invalid || this.thinking() || this.disabled()) return;
    this.send.emit(pregunta);
    this.control.reset('');
  }
}
