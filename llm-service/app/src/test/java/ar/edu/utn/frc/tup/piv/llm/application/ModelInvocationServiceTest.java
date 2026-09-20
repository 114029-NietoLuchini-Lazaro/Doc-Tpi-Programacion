package ar.edu.utn.frc.tup.piv.llm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidModelResponseException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.application.port.out.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationUnavailableException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
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
    when(configs.find(ModelFunction.TUTOR)).thenReturn(Optional.empty());
    Adapter adapter = request -> {
      throw new AssertionError("no debería invocarse el adaptador sin configuración");
    };
    var service = new ModelInvocationService(configs, adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(ModelInvocationUnavailableException.class);
  }

  @Test
  void rejectsAnOutOfSchemaResponseInsteadOfPropagatingIt() {
    Adapter adapter = request -> new ModelInvocationResult("", "fake", "fake-socratic-v1");
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(InvalidModelResponseException.class);
  }

  @Test
  void rejectsATutorResponseThatExceedsTheSchemaLength() {
    Adapter adapter = request -> new ModelInvocationResult("x".repeat(4001), "fake", "fake-socratic-v1");
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(InvalidModelResponseException.class)
        .hasMessageContaining("supera el límite");
  }

  @Test
  void preservesAngleBracketsReturnedByTheModel() {
    String reply = "Usá <div>hola</div>";
    Adapter adapter = request -> new ModelInvocationResult(reply, "openai-compatible", "model");
    var service = serviceWithAdapter(adapter);

    var result = service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1));

    assertThat(result.text()).isEqualTo(reply);
  }

  @Test
  void preservesMarkdownCodeBlocksWithAngleBrackets() {
    String reply = "```html\n<div class=\"test\">Hola</div>\n```";
    Adapter adapter = request -> new ModelInvocationResult(reply, "openai-compatible", "model");
    var service = serviceWithAdapter(adapter);

    var result = service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1));

    assertThat(result.text()).isEqualTo(reply);
  }

  @Test
  void preservesLiteralUnicodeEscapeTextWithoutSpeculativeDecoding() {
    String reply = "\\u003cdiv\\u003e y \\\\u003cdiv\\\\u003e";
    Adapter adapter = request -> new ModelInvocationResult(reply, "openai-compatible", "model");
    var service = serviceWithAdapter(adapter);

    var result = service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1));

    assertThat(result.text()).isEqualTo(reply);
  }

  @Test
  void wrapsProviderFailuresAsModelUnavailableAndPreservesTheCause() {
    var providerFailure = new ProviderException("PROVIDER_AUTHENTICATION_FAILED", "credencial inválida");
    Adapter adapter = request -> { throw providerFailure; };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(ModelInvocationUnavailableException.class)
        .hasCause(providerFailure);
  }

  @Test
  void wrapsMissingProviderModelAsModelUnavailableAndPreservesTheCause() {
    var providerFailure = new ProviderException("PROVIDER_MODEL_NOT_FOUND", "modelo inexistente");
    Adapter adapter = request -> { throw providerFailure; };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(ModelInvocationUnavailableException.class)
        .hasCause(providerFailure);
  }

  @Test
  void unwrapsCompletionExceptionBeforeClassifyingProviderFailure() {
    var providerFailure = new ProviderException("PROVIDER_UNAVAILABLE", "caído");
    Adapter adapter = request -> { throw new CompletionException(providerFailure); };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(ModelInvocationUnavailableException.class)
        .hasCause(providerFailure);
  }

  @Test
  void preservesTimeoutSemanticsThroughCompletionException() {
    var timeout = new ModelTimeoutException("lento");
    Adapter adapter = request -> { throw new CompletionException(timeout); };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isSameAs(timeout);
  }

  @Test
  void doesNotTreatUnexpectedCompletionCauseAsModelUnavailable() {
    var unexpected = new NullPointerException("bug");
    Adapter adapter = request -> { throw new CompletionException(unexpected); };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(ModelInvocationUnavailableException.class)
        .hasCause(unexpected);
  }

  @Test
  void propagatesErrorsInsteadOfWrappingThemAsApplicationExceptions() {
    var fatal = new AssertionError("fatal");
    Adapter adapter = request -> { throw new CompletionException(fatal); };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isSameAs(fatal);
  }

  @Test
  void unexpectedAdapterErrorsAreNotTreatedAsKnownUnavailability() {
    var unexpected = new NullPointerException("bug");
    Adapter adapter = request -> { throw unexpected; };
    var service = serviceWithAdapter(adapter);

    assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(ModelInvocationUnavailableException.class)
        .hasCause(unexpected);
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

  private ModelInvocationService serviceWithAdapter(Adapter adapter) {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config(java.util.UUID.randomUUID(), true)));
    return new ModelInvocationService(configs, adapter);
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
}
