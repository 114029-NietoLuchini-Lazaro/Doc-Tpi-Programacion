package ar.edu.utn.frc.tup.piv.llm.infrastructure.ai;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway.ProviderLlmGateway.Provider;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.FunctionModelConfigRepository;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Real model boundary for runtime use. Credentials are decrypted only for the invocation and the
 * expected challenge solution never enters this adapter or a model prompt.
 */
@Component
public class LangChain4jModelAdapter implements ModelInvocationPort {
  private final ProviderCredentialRepository deployments;
  private final FunctionModelConfigRepository configs;
  private final EncryptedSecretService secrets;

  public LangChain4jModelAdapter(ProviderCredentialRepository deployments, FunctionModelConfigRepository configs, EncryptedSecretService secrets) {
    this.deployments = deployments;
    this.configs = configs;
    this.secrets = secrets;
  }

  @Override
  public ModelInvocationResult invoke(ModelInvocationRequest request) {
    var configured = configs.find(request.function())
        .orElseThrow(() -> new IllegalStateException("La función no tiene modelo configurado"));
    Provider provider;
    try { provider = Provider.valueOf(configured.provider().toUpperCase()); }
    catch (IllegalArgumentException error) { throw new IllegalStateException("La función no apunta a un proveedor real", error); }
    var deployment = deployments.forProviderModel(provider, configured.modelId())
        .orElseThrow(() -> new IllegalStateException("No hay un despliegue disponible para la función"));
    var credential = deployments.get(deployment.credentialId())
        .filter(item -> "ACTIVE".equals(item.state()))
        .orElseThrow(() -> new IllegalStateException("La credencial del despliegue no está activa"));
    String secret = secrets.decrypt(credential.encryptedSecret(), credential.nonce());
    ChatModel model = model(deployment.provider(), credential.baseUrl(), secret, deployment.modelId(), request.timeout());
    String response = model.chat(request.systemPrompt() + "\n\n" + request.userPrompt());
    return new ModelInvocationResult(response, deployment.provider().name(), deployment.modelId());
  }

  private ChatModel model(Provider provider, String baseUrl, String secret, String modelId, Duration timeout) {
    return switch (provider) {
      case OPENAI_COMPATIBLE -> OpenAiChatModel.builder()
          .baseUrl(normalizeOpenAiBaseUrl(baseUrl)).apiKey(secret).modelName(modelId)
          .timeout(timeout).logRequests(false).logResponses(false).build();
      case ANTHROPIC -> {
        var builder = AnthropicChatModel.builder().apiKey(secret).modelName(modelId)
            .timeout(timeout).logRequests(false).logResponses(false);
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
        yield builder.build();
      }
      case GEMINI -> {
        var builder = GoogleAiGeminiChatModel.builder().apiKey(secret).modelName(modelId)
            .timeout(timeout).logRequests(false).logResponses(false);
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
        yield builder.build();
      }
    };
  }

  private String normalizeOpenAiBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) return "https://api.openai.com/v1";
    return baseUrl.endsWith("/v1") ? baseUrl : baseUrl.replaceFirst("/+$", "") + "/v1";
  }

  @Override public String provider() { return "runtime"; }
  @Override public String model() { return "deployment-selected"; }
}
