package ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.Test;

class ProviderInvocationGatewayTest {
  private final EncryptedSecretService secrets = mock(EncryptedSecretService.class);
  private final ProviderInvocationGateway gateway =
      new ProviderInvocationGateway(mock(ProviderRegistry.class), secrets, new ObjectMapper());

  @Test
  void invalidCredentialJsonBecomesAnInvalidCredentialProviderException() {
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenReturn("not-json");

    assertThatThrownBy(() -> gateway.material(credential()))
        .isInstanceOf(ProviderException.class)
        .extracting("code")
        .isEqualTo("INVALID_CREDENTIAL");
  }

  @Test
  void invalidCredentialCiphertextBecomesAnInvalidCredentialProviderException() {
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
    var failure = new IllegalStateException("infraestructura caída", new RuntimeException("bug"));
    when(secrets.decrypt(any(byte[].class), any(byte[].class))).thenThrow(failure);

    assertThatThrownBy(() -> gateway.material(credential()))
        .isSameAs(failure);
  }

  private ProviderCredentialRepository.Credential credential() {
    return new ProviderCredentialRepository.Credential(
        UUID.randomUUID(), "stub-provider", "test", Map.of(), new byte[] {1}, new byte[] {2},
        "••••", "ACTIVE", Instant.now());
  }
}
