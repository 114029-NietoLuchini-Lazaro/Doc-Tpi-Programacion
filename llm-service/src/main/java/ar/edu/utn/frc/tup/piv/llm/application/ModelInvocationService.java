package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.application.gateway.GatewayBudget;
import ar.edu.utn.frc.tup.piv.llm.application.gateway.GatewayPolicy;
import ar.edu.utn.frc.tup.piv.llm.application.gateway.GatewayUsageLog;
import ar.edu.utn.frc.tup.piv.llm.application.gateway.GatewayUsageLog.Outcome;
import ar.edu.utn.frc.tup.piv.llm.application.gateway.ProviderBreakers;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidModelResponseException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ProviderUnavailableException;
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
  private final GatewayPolicy policy;
  private final GatewayBudget budget;
  private final GatewayUsageLog usageLog;
  private final ProviderBreakers breakers;

  @org.springframework.beans.factory.annotation.Autowired
  public ModelInvocationService(FunctionModelConfigRepository configs, List<ModelInvocationPort> adapters,
      GatewayBudget budget, GatewayUsageLog usageLog) {
    this(configs, adapters, GatewayPolicy.defaults(), budget, usageLog);
  }

  public ModelInvocationService(FunctionModelConfigRepository configs, List<ModelInvocationPort> adapters,
      GatewayPolicy policy, GatewayBudget budget, GatewayUsageLog usageLog) {
    this.configs = configs;
    this.adapters = adapters.stream()
        .collect(Collectors.toMap(
            port -> port.provider().toLowerCase(Locale.ROOT),
            Function.identity(),
            (existing, replacement) -> existing));
    this.policy = policy;
    this.budget = budget;
    this.usageLog = usageLog;
    this.breakers = new ProviderBreakers(policy);
  }

  /** Sin reintentos, breaker ni presupuesto: comportamiento previo a EP-02·H03. */
  public ModelInvocationService(FunctionModelConfigRepository configs, List<ModelInvocationPort> adapters) {
    this(configs, adapters, GatewayPolicy.disabled(), new GatewayBudget(), new GatewayUsageLog());
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

    int inTokens = GatewayUsageLog.estimateTokens(systemPrompt) + GatewayUsageLog.estimateTokens(userPrompt);
    try {
      budget.check(function);
    } catch (RuntimeException exception) {
      usageLog.record(function, providerKey, config.modelId(), 0, Outcome.BUDGET_EXCEEDED, inTokens, 0, 0);
      throw exception;
    }
    var breaker = policy.breakerEnabled() ? breakers.of(providerKey) : null;
    if (breaker != null && !breaker.tryAcquirePermission()) {
      usageLog.record(function, providerKey, config.modelId(), 0, Outcome.BREAKER_OPEN, inTokens, 0, 0);
      throw new ProviderUnavailableException("El proveedor '" + providerKey + "' no está disponible (circuit breaker abierto)");
    }

    var request = new ModelInvocationRequest(function, systemPrompt, userPrompt, timeout);
    long started = System.nanoTime();
    RuntimeException last = null;
    int attempt = 0;
    while (attempt < policy.maxAttempts()) {
      attempt++;
      try {
        ModelInvocationResult result = callOnce(function, adapter, request, timeout);
        try {
          schema.validate(function, result.text());
        } catch (InvalidModelResponseException invalid) {
          // El proveedor respondió: no es una falla de disponibilidad, no se reintenta.
          if (breaker != null) breaker.onSuccess(elapsed(started), TimeUnit.MILLISECONDS);
          usageLog.record(function, providerKey, config.modelId(), elapsed(started), Outcome.INVALID_RESPONSE,
              inTokens, GatewayUsageLog.estimateTokens(result.text()), attempt);
          throw invalid;
        }
        if (breaker != null) breaker.onSuccess(elapsed(started), TimeUnit.MILLISECONDS);
        int outTokens = GatewayUsageLog.estimateTokens(result.text());
        usageLog.record(function, providerKey, config.modelId(), elapsed(started), Outcome.OK, inTokens, outTokens, attempt);
        budget.record(function, GatewayUsageLog.costOf(providerKey, inTokens, outTokens));
        return result;
      } catch (ModelTimeoutException | TransientProviderFailure exception) {
        last = exception instanceof TransientProviderFailure t ? t.asIllegalState() : (ModelTimeoutException) exception;
        if (attempt < policy.maxAttempts()) pause();
      }
    }
    if (breaker != null) breaker.onError(elapsed(started), TimeUnit.MILLISECONDS, last);
    usageLog.record(function, providerKey, config.modelId(), elapsed(started),
        last instanceof ModelTimeoutException ? Outcome.TIMEOUT : Outcome.PROVIDER_ERROR, inTokens, 0, attempt);
    throw last;
  }

  private ModelInvocationResult callOnce(ModelFunction function, ModelInvocationPort adapter,
      ModelInvocationRequest request, Duration timeout) {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      return CompletableFuture.supplyAsync(() -> adapter.invoke(request), executor)
          .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.TimeoutException exception) {
      throw new ModelTimeoutException(
          "El adaptador de " + name(function) + " superó el timeout de " + timeout.toMillis() + "ms");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Invocación de " + name(function) + " interrumpida", exception);
    } catch (ExecutionException exception) {
      throw new TransientProviderFailure("Fallo al invocar el modelo de " + name(function), exception.getCause());
    } finally {
      executor.shutdownNow();
    }
  }

  private long elapsed(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }

  private void pause() {
    if (policy.backoff().isZero()) return;
    try {
      Thread.sleep(policy.backoff().toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  /** Falla del adaptador (candidata a reintento); se expone como IllegalStateException al agotar intentos. */
  private static final class TransientProviderFailure extends RuntimeException {
    TransientProviderFailure(String message, Throwable cause) { super(message, cause); }

    IllegalStateException asIllegalState() { return new IllegalStateException(getMessage(), getCause()); }
  }

  private String name(ModelFunction function) {
    return function.name().toLowerCase(Locale.ROOT);
  }
}
