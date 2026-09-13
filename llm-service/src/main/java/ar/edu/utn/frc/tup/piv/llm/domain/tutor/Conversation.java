package ar.edu.utn.frc.tup.piv.llm.domain.tutor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Una conversación de tutoría con histórico multi-turno (EP-05, revisitando la decisión de
 * `docs/estado-implementacion/ep-05/interactions.md` de no portar
 * `codigo-ejemplo/ms-evaluacion-llm`/`demoLLMSpringAi` — ver `docs/estado-implementacion/ep-05/conversations.md`).
 * Portado de `demoLLMSpringAi/.../model/Conversacion.java`, agregando `courseCohortId`/
 * `learnerId`/`challengeId` que el original no tenía (necesarios para particionar y para
 * reutilizar `TutorInteractionService` con o sin conversación). */
public record Conversation(
    UUID id,
    UUID courseCohortId,
    UUID learnerId,
    UUID challengeId,
    String titulo,
    String estado,
    OffsetDateTime createdAt) {

  public static final String ESTADO_ABIERTA = "ABIERTA";

  public static Conversation nueva(UUID courseCohortId, UUID learnerId, UUID challengeId, String titulo) {
    String tituloEfectivo = (titulo == null || titulo.isBlank()) ? "Nueva conversación" : titulo.trim();
    return new Conversation(UUID.randomUUID(), courseCohortId, learnerId, challengeId, tituloEfectivo, ESTADO_ABIERTA, OffsetDateTime.now());
  }
}
