package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.application.port.out.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidModelResponseException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationUnavailableException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelResponseSchema;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** El caso de uso de `LLM-S01-H10`: resuelve la función contra
 * {@link FunctionModelConfigRepository}, invoca el {@link ModelInvocationPort} con timeout, y
 * valida la respuesta contra el schema. Reutilizable por cualquier función futura, no solo el
 * tutor — hoy el único llamador es {@link TutorInteractionService}. */
@Service
public class ModelInvocationService {
  private static final int MAX_ASYNC_WRAPPER_DEPTH = 8;
  private final FunctionModelConfigRepository configs;
  private final ModelInvocationPort adapter;
  private final ModelResponseSchema schema = new ModelResponseSchema();

  public ModelInvocationService(FunctionModelConfigRepository configs, ModelInvocationPort adapter) {
    this.configs = configs;
    this.adapter = adapter;
  }

  public ModelInvocationResult invoke(ModelFunction function, String systemPrompt, String userPrompt, Duration timeout) {
    var config = configs.find(function)
        .orElseThrow(() -> new ModelInvocationUnavailableException("La función " + name(function) + " no tiene modelo asignado"));
    if (!config.enabled()) {
      throw new ModelInvocationUnavailableException("La función " + name(function) + " está deshabilitada");
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
      throw invocationFailure(function, exception.getCause());
    } finally {
      executor.shutdownNow();
    }
  }

  private RuntimeException invocationFailure(ModelFunction function, Throwable cause) {
    cause = unwrapKnownAsyncWrappers(cause);
    if (cause instanceof Error error) throw error;
    if (cause instanceof ModelInvocationUnavailableException exception) return exception;
    if (cause instanceof ProviderException exception) {
      return new ModelInvocationUnavailableException(
          "El proveedor del modelo de " + name(function) + " no pudo responder", exception);
    }
    if (cause instanceof ModelTimeoutException exception) return exception;
    if (cause instanceof InvalidModelResponseException exception) return exception;
    if (cause instanceof RuntimeException exception) {
      return new IllegalStateException("Fallo inesperado al invocar el modelo de " + name(function), exception);
    }
    return new IllegalStateException("Fallo inesperado al invocar el modelo de " + name(function), cause);
  }

  private Throwable unwrapKnownAsyncWrappers(Throwable cause) {
    Throwable current = cause;
    for (int depth = 0; depth < MAX_ASYNC_WRAPPER_DEPTH; depth++) {
      if (!(current instanceof ExecutionException) && !(current instanceof CompletionException)) return current;
      Throwable nested = current.getCause();
      if (nested == null || nested == current) return current;
      current = nested;
    }
    return current;
  }

  private String name(ModelFunction function) {
    return function.name().toLowerCase(Locale.ROOT);
  }
}
