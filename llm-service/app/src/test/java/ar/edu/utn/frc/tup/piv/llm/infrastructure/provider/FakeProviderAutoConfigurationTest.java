package ar.edu.utn.frc.tup.piv.llm.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.provider.fake.FakeAutoConfiguration;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class FakeProviderAutoConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(FakeAutoConfiguration.class))
      .withUserConfiguration(RegistryConfiguration.class);

  @Test
  void fakeIsDisabledWhenThePropertyIsAbsent() {
    runner.run(context -> {
      assertThat(context.getBeansOfType(AiProviderAdapter.class)).isEmpty();
      assertMissingFromRegistry(context.getBean(ProviderRegistry.class));
    });
  }

  @Test
  void fakeIsDisabledWhenThePropertyIsFalse() {
    runner.withPropertyValues("llm.provider.fake.enabled=false").run(context -> {
      assertThat(context.getBeansOfType(AiProviderAdapter.class)).isEmpty();
      assertMissingFromRegistry(context.getBean(ProviderRegistry.class));
    });
  }

  @Test
  void fakeIsRegisteredOnlyWhenThePropertyIsTrue() {
    runner.withPropertyValues("llm.provider.fake.enabled=true").run(context -> {
      var adapters = context.getBeansOfType(AiProviderAdapter.class);
      assertThat(adapters).hasSize(1);
      var fake = adapters.values().iterator().next();

      assertThat(fake.descriptor().key()).isEqualTo("fake");
      assertThat(context.getBean(ProviderRegistry.class).required("fake")).isSameAs(fake);
    });
  }

  private void assertMissingFromRegistry(ProviderRegistry registry) {
    assertThatThrownBy(() -> registry.required("fake"))
        .isInstanceOf(ProviderException.class)
        .extracting("code")
        .isEqualTo("PROVIDER_NOT_INSTALLED");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RegistryConfiguration {
    @Bean
    ProviderRegistry providerRegistry(List<AiProviderAdapter> adapters) {
      return new ProviderRegistry(adapters);
    }
  }
}
