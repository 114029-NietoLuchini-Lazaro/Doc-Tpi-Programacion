package ar.edu.utn.frc.tup.piv.llm.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
  @Test
  void resolvesAnAdapterByItsStableProviderKey() {
    var adapter = new StubAdapter("sample-provider");

    var registry = new ProviderRegistry(List.of(adapter));

    assertThat(registry.required("sample-provider")).isSameAs(adapter);
    assertThat(registry.descriptors()).extracting(ProviderDescriptor::key)
        .containsExactly("sample-provider");
  }

  @Test
  void rejectsDuplicateProviderKeysAtStartup() {
    assertThatThrownBy(() -> new ProviderRegistry(List.of(
        new StubAdapter("sample-provider"), new StubAdapter("sample-provider"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("más de un adaptador");
  }

  private static final class StubAdapter implements AiProviderAdapter {
    private final ProviderDescriptor descriptor;

    private StubAdapter(String key) {
      descriptor = new ProviderDescriptor(key, key, "test", List.of(),
          new ProviderCapabilities(false, false, false, false, false, false, false, false));
    }

    @Override public ProviderDescriptor descriptor() { return descriptor; }
    @Override public void validate(ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial credential) { }
    @Override public List<ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor> discoverModels(
        ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial credential) { return List.of(); }
    @Override public ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderReply invoke(
        ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial credential,
        ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderInvocation invocation) { return null; }
  }
}
