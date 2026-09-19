package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelResponseSchema;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.FunctionModelConfigRepository;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** El caso de uso de `LLM-S01-H10` y `LLM-EP02-H02`: resuelve la función contra
 * {@link FunctionModelConfigRepository}, selecciona el {@link ModelInvocationPort} según el
 * proveedor configurado en la base de datos, lo invoca con timeout, y valida la respuesta contra
 * el schema. Permite alternar proveedores en runtime sin redespliegue (CA2). */
@Service
public class ModelInvocationService {
  private final FunctionModelConfigRepository configs;
  private final Map<String, ModelInvocationPort> adapters;
  private final ModelResponseSchema schema = new ModelResponseSchema();

  @org.springframework.beans.factory.annotation.Autowired
  public ModelInvocationService(FunctionModelConfigRepository configs, List<ModelInvocationPort> adapters) {
    this.configs = configs;
    this.adapters = adapters.stream()
        .collect(Collectors.toMap(
            port -> port.provider().toLowerCase(Locale.ROOT),
            Function.identity(),
            (existing, replacement) -> existing));
  }

  public ModelInvocationService(FunctionModelConfigRepository configs, ModelInvocationPort adapter) {
    this(configs, List.of(adapter));
  }

  public ModelInvocationResult invoke(ModelFunction function, String systemPrompt, String userPrompt, Duration timeout) {
    var config = configs.find(function)
        .orElseThrow(() -> new IllegalStateException("La función " + name(function) + " no tiene modelo asignado"));
    if (!config.enabled()) {
      throw new IllegalStateException("La función " + name(function) + " está deshabilitada");
    }

    String providerKey = config.provider() != null ? config.provider().toLowerCase(Locale.ROOT) : "";
    ModelInvocationPort adapter = adapters.get(providerKey);
    if (adapter == null) {
      throw new IllegalStateException(
          "No hay adaptador registrado para el proveedor '" + config.provider() + "' asignado a " + name(function));
    }

    var request = new ModelInvocationRequest(function, systemPrompt, userPrompt, timeout);
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      ModelInvocationResult result = CompletableFuture.supplyAsync(() -> adapter.invoke(request), executor)
          .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      schema.validate(function, result.text());
      return result;
    } catch (java.util.concurrent.TimeoutException exception) {
      throw new ModelTimeoutException(
          "El adaptador de " + name(function) + " superó el timeout de " + timeout.toMillis() + "ms");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Invocación de " + name(function) + " interrumpida", exception);
    } catch (ExecutionException exception) {
      throw new IllegalStateException("Fallo al invocar el modelo de " + name(function), exception.getCause());
    } finally {
      executor.shutdownNow();
    }
  }

  private String name(ModelFunction function) {
    return function.name().toLowerCase(Locale.ROOT);
  }
}
