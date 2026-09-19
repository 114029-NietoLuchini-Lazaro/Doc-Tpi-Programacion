package ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence;

import ar.edu.utn.frc.tup.piv.llm.domain.tutor.ExpectedSolutionProvider;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Implementación temporal en memoria de {@link ExpectedSolutionProvider}: sin soluciones
 * cargadas devuelve vacío (el guardarraíl usa solo su heurística de longitud de código).
 *
 * <p>TODO(EP-05→Tema-05): reemplazar por el adaptador real cuando el Tema 05 (Desafíos Prácticos)
 * publique su contrato. Alcanza con cambiar este adaptador; el caso de uso y el guardarraíl no
 * cambian. */
@Component
public class FakeExpectedSolutionProvider implements ExpectedSolutionProvider {
  private final Map<UUID, String> solutions = new ConcurrentHashMap<>();

  /** Solo para pruebas/desarrollo: registra a mano la solución de un desafío. */
  public void register(UUID challengeId, String solution) {
    solutions.put(challengeId, solution);
  }

  @Override
  public Optional<String> forChallenge(UUID challengeId) {
    return Optional.ofNullable(challengeId).map(solutions::get);
  }
}
