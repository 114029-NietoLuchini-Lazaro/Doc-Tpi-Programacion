package ar.edu.utn.frc.tup.piv.llm.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.tup.piv.llm.provider.spi.InferenceSettings;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderInvocation;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FakeProviderAdapterTest {
  private final FakeProviderAdapter adapter = new FakeProviderAdapter();
  private final ProviderCredentialMaterial material =
      new ProviderCredentialMaterial(FakeProviderAdapter.PROVIDER_KEY, Map.of(), Map.of());

  @Test
  void returnsADeterministicValidResponseWithoutCredentials() {
    var first = adapter.invoke(material, invocation(FakeProviderAdapter.VALID_MODEL, Duration.ofSeconds(1)));
    var second = adapter.invoke(material, invocation(FakeProviderAdapter.VALID_MODEL, Duration.ofSeconds(1)));

    assertThat(first.text()).isNotBlank();
    assertThat(first.text()).isEqualTo(second.text());
    assertThat(first.providerFingerprint()).isEqualTo("fake-in-process");
  }

  @Test
  void returnsAnOutOfSchemaResponseForTheInvalidModel() {
    var result = adapter.invoke(material, invocation(FakeProviderAdapter.INVALID_MODEL, Duration.ofSeconds(1)));

    assertThat(result.text()).isBlank();
  }

  @Test
  void delaysTheSlowModelBeyondTheConfiguredTimeout() {
    long started = System.nanoTime();

    adapter.invoke(material, invocation(FakeProviderAdapter.SLOW_MODEL, Duration.ofMillis(50)));

    long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
    assertThat(elapsedMillis).isGreaterThanOrEqualTo(140);
  }

  @Test
  void rejectsUnknownFakeModels() {
    assertThatThrownBy(() -> adapter.invoke(material, invocation("fake-unknown-v1", Duration.ofSeconds(1))))
        .isInstanceOf(ProviderException.class)
        .extracting("code")
        .isEqualTo("PROVIDER_MODEL_NOT_FOUND");
  }

  private ProviderInvocation invocation(String modelId, Duration timeout) {
    return new ProviderInvocation(modelId, "system\n\n¿cómo ordeno una lista?",
        new InferenceSettings("test", null, null, null, null, false, 512), timeout);
  }
}
