package ar.edu.utn.frc.tup.piv.llm.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.LangChain4jModelAdapter;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.application.service.ModelInvocationService;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidModelResponseException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.provider.fake.FakeAutoConfiguration;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FakeProviderRuntimeIntegrationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(FakeAutoConfiguration.class))
      .withPropertyValues("llm.provider.fake.enabled=true");

  @Test
  void enabledFakeTraversesTheRuntimeInvocationChain() {
    runner.run(context -> {
      var service = serviceFor(context.getBean(AiProviderAdapter.class), "fake-socratic-v1");

      var result = service.invoke(ModelFunction.TUTOR, "system", "¿cómo ordeno una lista?", Duration.ofSeconds(1));

      assertThat(result.provider()).isEqualTo("fake");
      assertThat(result.model()).isEqualTo("fake-socratic-v1");
      assertThat(result.text()).isNotBlank();
    });
  }

  @Test
  void invalidFakeResponseIsValidatedByTheRuntimeService() {
    runner.run(context -> {
      var service = serviceFor(context.getBean(AiProviderAdapter.class), "fake-invalid-v1");

      assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
          .isInstanceOf(InvalidModelResponseException.class);
    });
  }

  @Test
  void slowFakeModelTimesOutThroughTheRuntimeInvocationChain() {
    runner.run(context -> {
      var service = serviceFor(context.getBean(AiProviderAdapter.class), "fake-slow-v1");

      assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofMillis(50)))
          .isInstanceOf(ModelTimeoutException.class);
    });
  }

  private ModelInvocationService serviceFor(AiProviderAdapter fake, String modelId) {
    UUID credentialId = UUID.randomUUID();
    UUID deploymentId = UUID.randomUUID();
    var credential = new ProviderCredentialRepository.Credential(
        credentialId, "fake", "fake", Map.of(), new byte[] {1}, new byte[] {2}, "••••", "ACTIVE", Instant.now());
    var deployment = new ProviderCredentialRepository.Deployment(
        deploymentId, credentialId, "fake", "fake", modelId, "ACTIVE", Instant.now(), 1, null, Map.of());

    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config(deploymentId, true)));
    var deployments = mock(ProviderCredentialRepository.class);
    when(deployments.forId(deploymentId)).thenReturn(Optional.of(deployment));
    when(deployments.get(credentialId)).thenReturn(Optional.of(credential));

    var secrets = mock(EncryptedSecretService.class);
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenReturn("{}");
    var registry = new ProviderRegistry(List.of(fake));
    var gateway = new ProviderInvocationGateway(registry, secrets, new ObjectMapper());
    var port = new LangChain4jModelAdapter(deployments, configs, gateway);
    return new ModelInvocationService(configs, port);
  }
}
