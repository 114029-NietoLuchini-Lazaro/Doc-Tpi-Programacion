package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidEmbeddingException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.FunctionModelConfigRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** Paralelo a {@link ModelInvocationService} pero para el puerto de vectorización (EP-09): resuelve
 * la función `embedding` contra {@link FunctionModelConfigRepository}, invoca el
 * {@link EmbeddingPort} con timeout, y valida la dimensión del vector. No reutiliza
 * `ModelInvocationService` porque ese servicio está atado a texto de salida
 * ({@code ModelInvocationResult}), no a un vector — forzar el embedding ahí ensuciaría ese
 * contrato (ver plan de EP-09, §4). */
@Service
public class EmbeddingInvocationService {
  private static final int EXPECTED_DIMENSIONS = 768;

  private final FunctionModelConfigRepository configs;
  private final EmbeddingPort adapter;

  public EmbeddingInvocationService(FunctionModelConfigRepository configs, EmbeddingPort adapter) {
    this.configs = configs;
    this.adapter = adapter;
  }

  /** Vectoriza un único texto donde el resultado es indispensable (ej. la pregunta del alumno
   * antes de buscar contexto) — nunca devuelve un vector nulo o de otra dimensión. */
  public EmbeddingResult embed(String text, Duration timeout) {
    requireEnabled();
    EmbeddingResult result = runWithTimeout(() -> adapter.embed(text), timeout);
    validate(result);
    return result;
  }

  /** Vectoriza un lote (ej. los chunks de un PDF recién indexado). A diferencia de {@link #embed},
   * tolera vectores individuales nulos dentro del lote — mismo criterio que
   * `EmbeddingService.computeEmbeddings` en la demo: un fragmento sin vector no aborta la
   * indexación completa, solo queda sin buscar semánticamente ese fragmento puntual. */
  public List<EmbeddingResult> embedBatch(List<String> texts, Duration timeout) {
    if (texts == null || texts.isEmpty()) return List.of();
    requireEnabled();
    return runWithTimeout(() -> adapter.embedBatch(texts), timeout);
  }

  private void requireEnabled() {
    var config = configs.find(ModelFunction.EMBEDDING)
        .orElseThrow(() -> new IllegalStateException("La función embedding no tiene modelo asignado"));
    if (!config.enabled()) {
      throw new IllegalStateException("La función embedding está deshabilitada");
    }
  }

  private <T> T runWithTimeout(java.util.function.Supplier<T> call, Duration timeout) {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      return CompletableFuture.supplyAsync(call, executor).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.TimeoutException exception) {
      throw new EmbeddingTimeoutException("El adaptador de embeddings superó el timeout de " + timeout.toMillis() + "ms");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Invocación de embeddings interrumpida", exception);
    } catch (ExecutionException exception) {
      throw new IllegalStateException("Fallo al invocar el adaptador de embeddings", exception.getCause());
    } finally {
      executor.shutdownNow();
    }
  }

  private void validate(EmbeddingResult result) {
    if (result == null || result.vector() == null) {
      throw new InvalidEmbeddingException("El adaptador de embeddings devolvió un vector vacío");
    }
    if (result.vector().length != EXPECTED_DIMENSIONS) {
      throw new InvalidEmbeddingException("El vector tiene " + result.vector().length + " dimensiones, se esperaban " + EXPECTED_DIMENSIONS);
    }
  }
}
