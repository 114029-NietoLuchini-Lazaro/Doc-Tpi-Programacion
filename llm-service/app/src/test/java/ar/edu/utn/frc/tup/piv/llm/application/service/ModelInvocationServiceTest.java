package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ModelDeploymentRepository;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelDeploymentSummary;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidModelResponseException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.application.port.out.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelInvocationServiceTest {

  @Test
  void invokesTheAdapterWhenTheFunctionIsEnabled() {
    Adapter adapter = request -> new ModelInvocationResult("una pista socrática", "fake", "fake-socratic-v1");
    var service = serviceWithAdapter(adapter);

    var result = service.invoke(ModelFunction.TUTOR, "system", "¿cómo ordeno una lista?", Duration.ofSeconds(1));

    assertThat(result.text()).isEqualTo("una pista socrática");
  }

  @Test
  void refusesToInvokeAFunctionWithoutModelAssigned() {
    var configs = mock(FunctionModelConfigRepository.class);
    var deployments = mock(ModelDeploymentRepository.class);
    when(configs.find(ModelFunction.TUTOR)).thenReturn(Optional.empty());
    Adapter adapter = request -> {
      throw new AssertionError("no debería invocarse el adaptador sin configuración");
    };
    var service = new ModelInvocationService(configs, deployments, adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsAnOutOfSchemaResponseInsteadOfPropagatingIt() {
    Adapter adapter = request -> new ModelInvocationResult("", "fake", "fake-socratic-v1");
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(InvalidModelResponseException.class);
  }

  @Test
  void cutsTheCallWhenTheAdapterExceedsTheConfiguredTimeout() {
    Adapter adapter = request -> {
      try {
        Thread.sleep(300);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      return new ModelInvocationResult("tarde", "fake", "fake-socratic-v1");
    };
    var service = serviceWithAdapter(adapter);

    long start = System.currentTimeMillis();
    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofMillis(50)))
        .isInstanceOf(ModelTimeoutException.class);
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed).isLessThan(300);
  }

  @Test
  void routesToConfiguredProviderDynamicallyWithoutRedeployment() {
    Adapter fakeAdapter = request -> new ModelInvocationResult("respuesta del fake", "fake", "fake-socratic-v1");
    ModelInvocationPort groqAdapter = new ModelInvocationPort() {
      @Override
      public ModelInvocationResult invoke(ModelInvocationRequest request) {
        return new ModelInvocationResult("respuesta real de groq", "groq", "llama-3.3-70b-versatile");
      }
      @Override
      public String provider() { return "groq"; }
      @Override
      public String model() { return "llama-3.3-70b-versatile"; }
    };

    var configs = mock(FunctionModelConfigRepository.class);
    var deployments = mock(ModelDeploymentRepository.class);
    // Primero configurado en groq:
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(asignado(deployments, "groq", "llama-3.3-70b-versatile", "1", true)));

    var service = new ModelInvocationService(configs, deployments, java.util.List.of(fakeAdapter, groqAdapter));

    var groqResult = service.invoke(ModelFunction.TUTOR, "system", "¿cómo ordeno?", Duration.ofSeconds(1));
    assertThat(groqResult.text()).isEqualTo("respuesta real de groq");
    assertThat(groqResult.provider()).isEqualTo("groq");

    // Luego cambia en la base a fake (sin redesplegar):
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(asignado(deployments, "fake", "fake-socratic-v1", "1", true)));

    var fakeResult = service.invoke(ModelFunction.TUTOR, "system", "¿cómo ordeno?", Duration.ofSeconds(1));
    assertThat(fakeResult.text()).isEqualTo("respuesta del fake");
    assertThat(fakeResult.provider()).isEqualTo("fake");
  }

  @Test
  void theModelAssignedInTheDatabaseReachesTheAdapter() {
    var received = new java.util.concurrent.atomic.AtomicReference<String>();
    Adapter adapter = request -> {
      received.set(request.modelId());
      return new ModelInvocationResult("ok", "fake", "fake-socratic-v1");
    };
    var configs = mock(FunctionModelConfigRepository.class);
    var deployments = mock(ModelDeploymentRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(asignado(deployments, "fake", "modelo-asignado", "1", true)));
    var service = new ModelInvocationService(configs, deployments, adapter);

    service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1));

    assertThat(received.get()).isEqualTo("modelo-asignado");
  }

  @Test
  void throwsExceptionWhenProviderIsNotRegistered() {
    Adapter fakeAdapter = request -> new ModelInvocationResult("pista", "fake", "fake-socratic-v1");
    var configs = mock(FunctionModelConfigRepository.class);
    var deployments = mock(ModelDeploymentRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(asignado(deployments, "unsupported-provider", "m1", "1", true)));

    var service = new ModelInvocationService(configs, deployments, java.util.List.of(fakeAdapter));

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No hay adaptador registrado para el proveedor 'unsupported-provider'");
  }

  private ModelInvocationService serviceWithAdapter(Adapter adapter) {
    var configs = mock(FunctionModelConfigRepository.class);
    var deployments = mock(ModelDeploymentRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config(java.util.UUID.randomUUID(), true)));
    return new ModelInvocationService(configs, deployments, adapter);
  }

  /** {@link ModelInvocationPort} declara `provider()`/`model()` además de `invoke()`; esta
   * subinterfaz con defaults lo vuelve funcional para poder pasar una lambda como test double sin
   * escribir una clase entera por escenario. */
  @FunctionalInterface
  private interface Adapter extends ModelInvocationPort {
    @Override
    default String provider() {
      return "fake";
    }

    @Override
    default String model() {
      return "fake-socratic-v1";
    }
  }

  /**
   * Desde la V26 la asignación función→modelo guarda el id del despliegue; proveedor y modelo se
   * leen de {@code ModelDeploymentRepository}. Registra el despliegue en el mock y devuelve el
   * Config correspondiente.
   */
  private static FunctionModelConfigRepository.Config asignado(ModelDeploymentRepository deployments,
      String provider, String modelId, String modelVersion, boolean enabled) {
    UUID deploymentId = UUID.randomUUID();
    when(deployments.byId(deploymentId)).thenReturn(Optional.of(
        new ModelDeploymentSummary(deploymentId, provider, modelId, modelVersion, "ENABLED")));
    return new FunctionModelConfigRepository.Config(deploymentId, enabled);
  }
}
