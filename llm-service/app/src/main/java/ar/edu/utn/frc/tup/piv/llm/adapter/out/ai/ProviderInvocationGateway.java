package ar.edu.utn.frc.tup.piv.llm.adapter.out.ai;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.InferenceSettings;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderInvocation;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderReply;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Map;
import java.util.function.Consumer;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import org.springframework.stereotype.Component;

/** Core gateway: decrypts only at the boundary and delegates to the selected Strategy. */
@Component
public class ProviderInvocationGateway {
  private final ProviderRegistry registry;
  private final EncryptedSecretService secrets;
  private final ObjectMapper json;

  public ProviderInvocationGateway(ProviderRegistry registry, EncryptedSecretService secrets, ObjectMapper json) {
    this.registry = registry; this.secrets = secrets; this.json = json;
  }

  public ProviderReply invoke(ProviderCredentialRepository.Credential credential, String modelId,
      String prompt, InferenceSettings settings, Duration timeout) {
    var adapter = registry.required(credential.providerKey());
    var material = material(credential);
    adapter.validate(material);
    return adapter.invoke(material, new ProviderInvocation(modelId, prompt, settings, timeout));
  }

  public ProviderReply stream(ProviderCredentialRepository.Credential credential, String modelId,
      String prompt, InferenceSettings settings, Duration timeout, Consumer<String> onDelta) {
    var adapter = registry.required(credential.providerKey());
    var material = material(credential);
    adapter.validate(material);
    return adapter.stream(material, new ProviderInvocation(modelId, prompt, settings, timeout), onDelta);
  }

  public ProviderCredentialMaterial material(ProviderCredentialRepository.Credential credential) {
    if (credential.encryptedSecrets() == null || credential.nonce() == null) {
      throw invalidCredential(new IllegalArgumentException("La credencial no contiene material cifrado"));
    }

    String payload;
    try {
      payload = secrets.decrypt(credential.encryptedSecrets(), credential.nonce());
    } catch (IllegalStateException exception) {
      if (isInvalidDecryption(exception)) throw invalidCredential(exception);
      throw exception;
    }
    if (payload == null) {
      throw invalidCredential(new IllegalArgumentException("El contenido de la credencial está vacío"));
    }

    Map<String, String> decrypted;
    try {
      decrypted = json.readValue(payload, new TypeReference<Map<String, String>>() { });
    } catch (JsonProcessingException exception) {
      throw invalidCredential(exception);
    }
    if (decrypted == null || decrypted.entrySet().stream()
        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
      throw invalidCredential(new IllegalArgumentException("El contenido de la credencial es incompleto"));
    }
    return new ProviderCredentialMaterial(credential.providerKey(), credential.configuration(), decrypted);
  }

  private boolean isInvalidDecryption(IllegalStateException exception) {
    Throwable cause = exception.getCause();
    return cause instanceof AEADBadTagException
        || cause instanceof BadPaddingException
        || cause instanceof IllegalBlockSizeException
        || cause instanceof InvalidAlgorithmParameterException
        || cause instanceof InvalidKeyException;
  }

  private ProviderException invalidCredential(Throwable cause) {
    return new ProviderException("INVALID_CREDENTIAL", "No se pudo preparar la credencial del proveedor", cause);
  }
}
