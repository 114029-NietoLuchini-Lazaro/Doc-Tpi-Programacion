package ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptedSecretServiceTest {
  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void encryptsAndDecryptsRoundTrip() {
    var service = new EncryptedSecretService(KEY);
    var encrypted = service.encrypt("mi-api-key-secreta");
    assertThat(new String(encrypted.value())).doesNotContain("mi-api-key-secreta");
    assertThat(service.decrypt(encrypted.value(), encrypted.nonce())).isEqualTo("mi-api-key-secreta");
  }

  @Test
  void usesADifferentNonceEachTime() {
    var service = new EncryptedSecretService(KEY);
    assertThat(service.encrypt("x").nonce()).isNotEqualTo(service.encrypt("x").nonce());
  }

  @Test
  void rejectsMissingOrMalformedMasterKey() {
    assertThatThrownBy(() -> new EncryptedSecretService("")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EncryptedSecretService(Base64.getEncoder().encodeToString(new byte[16])))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void decryptFailsWithTamperedCiphertext() {
    var service = new EncryptedSecretService(KEY);
    var encrypted = service.encrypt("abc");
    byte[] tampered = encrypted.value().clone();
    tampered[0] ^= 1;
    assertThatThrownBy(() -> service.decrypt(tampered, encrypted.nonce())).isInstanceOf(RuntimeException.class);
  }
}
