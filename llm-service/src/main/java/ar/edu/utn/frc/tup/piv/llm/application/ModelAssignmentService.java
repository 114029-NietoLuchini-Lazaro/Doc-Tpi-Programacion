package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.FunctionModelConfig;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.security.CallerIdentity;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Servicio de aplicación para consultar y actualizar la asignación de modelos por función de IA. */
@Service
public class ModelAssignmentService {
  private final FunctionModelConfigRepository repository;

  public ModelAssignmentService(FunctionModelConfigRepository repository) {
    this.repository = repository;
  }

  public Optional<FunctionModelConfig> find(ModelFunction function) {
    return repository.find(function)
        .map(c -> new FunctionModelConfig(c.provider(), c.modelId(), c.modelVersion(), c.enabled()));
  }

  public void upsert(ModelFunction function, String provider, String modelId, String modelVersion, CallerIdentity actor) {
    repository.upsert(function, provider, modelId, modelVersion, actor);
  }
}
