package ar.edu.utn.frc.tup.piv.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.LangChain4jModelAdapter;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.AuditRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.IdempotencyRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.application.service.ModelInvocationService;
import ar.edu.utn.frc.tup.piv.llm.application.service.TutorInteractionService;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderFailureIntegrationTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CallerIdentity actor = new CallerIdentity("practice-service", UUID.randomUUID(), "req-1", "trace-1");

  @Test
  void providerFailureTraversesTheRuntimeChainAndBecomesUnavailable() {
    UUID credentialId = UUID.randomUUID();
    UUID deploymentId = UUID.randomUUID();
    var credential = new ProviderCredentialRepository.Credential(
        credentialId, "stub-provider", "stub", Map.of(), new byte[] {1}, new byte[] {2},
        "••••", "ACTIVE", Instant.now());
    var deployment = new ProviderCredentialRepository.Deployment(
        deploymentId, credentialId, "stub-provider", "stub", "model", "ACTIVE", Instant.now(),
        1, null, Map.of());

    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config(deploymentId, true)));
    var deployments = mock(ProviderCredentialRepository.class);
    when(deployments.forId(deploymentId)).thenReturn(Optional.of(deployment));
    when(deployments.get(credentialId)).thenReturn(Optional.of(credential));

    var secrets = mock(EncryptedSecretService.class);
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenReturn("{\"apiKey\":\"test\"}");

    var provider = mock(AiProviderAdapter.class);
    when(provider.descriptor()).thenReturn(new ProviderDescriptor(
        "stub-provider", "Stub provider", "test", List.of(),
        new ProviderCapabilities(false, true, true, false, false, false, false, false)));
    var providerFailure = new ProviderException("PROVIDER_UNAVAILABLE", "provider down");
    doThrow(providerFailure).when(provider).invoke(any(), any());

    var registry = new ProviderRegistry(List.of(provider));
    var gateway = new ProviderInvocationGateway(registry, secrets, mapper);
    var runtimeAdapter = new LangChain4jModelAdapter(deployments, configs, gateway);
    var models = new ModelInvocationService(configs, runtimeAdapter);
    var idempotency = mock(IdempotencyRepository.class);
    when(idempotency.replay(anyString(), any(), any(), anyString())).thenReturn(Optional.empty());
    var tutor = new TutorInteractionService(models, idempotency, mock(AuditRepository.class), mapper, 1000);

    var response = tutor.respond(new TutorInteractionService.Request(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        "¿cómo ordeno una lista?", "medium", null), UUID.randomUUID(), actor);

    assertThat(response.state()).isEqualTo("unavailable");
    verify(provider).invoke(any(), any());
  }
}
