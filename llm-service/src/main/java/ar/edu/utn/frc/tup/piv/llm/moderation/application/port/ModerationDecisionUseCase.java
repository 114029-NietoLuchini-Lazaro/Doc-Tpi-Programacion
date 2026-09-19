package ar.edu.utn.frc.tup.piv.llm.moderation.application.port;

import ar.edu.utn.frc.tup.piv.llm.moderation.application.dto.ModerationDecisionCommand;
import ar.edu.utn.frc.tup.piv.llm.moderation.domain.ModerationDecision;

/**
 * Puerto de entrada primario para ejecutar la decisión de moderación.
 */
public interface ModerationDecisionUseCase {

    /**
     * Evalúa o recupera de forma idempotente la decisión de moderación para el comando provisto.
     */
    ModerationDecision decide(ModerationDecisionCommand command);
}
