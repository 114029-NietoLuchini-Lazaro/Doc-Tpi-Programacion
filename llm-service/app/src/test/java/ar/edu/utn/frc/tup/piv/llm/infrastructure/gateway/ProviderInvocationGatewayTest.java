package ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.CredentialField;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderInvocation;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderReply;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.Test;

class ProviderInvocationGatewayTest {
  @Test
  void invalidCredentialJsonBecomesAnInvalidCredentialProviderException() {
    var secrets = mock(EncryptedSecretService.class);
    var gateway = gatewayFor(secrets, requiredCredentialAdapter("stub-provider"));
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenReturn("not-json");

    assertThatThrownBy(() -> gateway.material(credential()))
        .isInstanceOf(ProviderException.class)
        .extracting("code")
        .isEqualTo("INVALID_CREDENTIAL");
  }

  @Test
  void invalidCredentialCiphertextBecomesAnInvalidCredentialProviderException() {
    var secrets = mock(EncryptedSecretService.class);
    var gateway = gatewayFor(secrets, requiredCredentialAdapter("stub-provider"));
    var failure = new IllegalStateException("No se pudo descifrar la credencial",
        new AEADBadTagException("bad tag"));
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenThrow(failure);

    assertThatThrownBy(() -> gateway.material(credential()))
        .isInstanceOf(ProviderException.class)
        .extracting("code")
        .isEqualTo("INVALID_CREDENTIAL");
  }

  @Test
  void unexpectedCredentialPreparationFailureIsPreserved() {
    var secrets = mock(EncryptedSecretService.class);
    var gateway = gatewayFor(secrets, requiredCredentialAdapter("stub-provider"));
    var failure = new IllegalStateException("infraestructura caída", new RuntimeException("bug"));
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenThrow(failure);

    assertThatThrownBy(() -> gateway.material(credential()))
        .isSameAs(failure);
  }

  @Test
  void providerWithoutCredentialFieldsUsesEmptyMaterialWithoutDecryptingSecrets() {
    var secrets = mock(EncryptedSecretService.class);
    var gateway = gatewayFor(secrets, noCredentialAdapter("fake"));
    var credential = new ProviderCredentialRepository.Credential(
        UUID.randomUUID(), "fake", "Fake provider", Map.of("mode", "local"), new byte[0], new byte[0],
        "sin secretos", "ACTIVE", Instant.now());

    var material = gateway.material(credential);

    assertThat(material.providerKey()).isEqualTo("fake");
    assertThat(material.configuration()).containsEntry("mode", "local");
    assertThat(material.secrets()).isEmpty();
    verify(secrets, never()).decrypt(any(byte[].class), any(byte[].class));
  }

  private ProviderInvocationGateway gatewayFor(EncryptedSecretService secrets, AiProviderAdapter adapter) {
    var registry = mock(ProviderRegistry.class);
    when(registry.required(adapter.descriptor().key())).thenReturn(adapter);
    return new ProviderInvocationGateway(registry, secrets, new ObjectMapper());
  }

  private ProviderCredentialRepository.Credential credential() {
    return new ProviderCredentialRepository.Credential(
        UUID.randomUUID(), "stub-provider", "test", Map.of(), new byte[] {1}, new byte[] {2},
        "••••", "ACTIVE", Instant.now());
  }

  private AiProviderAdapter requiredCredentialAdapter(String providerKey) {
    return adapter(providerKey, List.of(new CredentialField("apiKey", "API key", true, true, "test")));
  }

  private AiProviderAdapter noCredentialAdapter(String providerKey) {
    return adapter(providerKey, List.of());
  }

  private AiProviderAdapter adapter(String providerKey, List<CredentialField> fields) {
    var descriptor = new ProviderDescriptor(providerKey, providerKey, "1", fields,
        new ProviderCapabilities(false, false, false, false, false, false, false, false));
    return new AiProviderAdapter() {
      @Override public ProviderDescriptor descriptor() { return descriptor; }
      @Override public void validate(ProviderCredentialMaterial credential) { }
      @Override public List<ModelDescriptor> discoverModels(ProviderCredentialMaterial credential) { return List.of(); }
      @Override public ProviderReply invoke(ProviderCredentialMaterial credential, ProviderInvocation invocation) {
        return new ProviderReply("", 0, 0, "test");
      }
      @Override public ProviderReply stream(ProviderCredentialMaterial credential, ProviderInvocation invocation,
          Consumer<String> onDelta) {
        return invoke(credential, invocation);
      }
    };
  }
}
