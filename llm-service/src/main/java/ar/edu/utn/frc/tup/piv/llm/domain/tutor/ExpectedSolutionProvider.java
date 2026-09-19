package ar.edu.utn.frc.tup.piv.llm.domain.tutor;

import java.util.Optional;
import java.util.UUID;

/** Contrato de dominio para obtener la solución esperada de un desafío. Se usa únicamente para
 * alimentar el guardarraíl de salida anti-fuga; jamás se incluye en el prompt del modelo. */
public interface ExpectedSolutionProvider {

  Optional<String> forChallenge(UUID challengeId);
}
