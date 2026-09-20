package ar.edu.utn.frc.tup.piv.llm.provider.openai;

import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.CredentialField;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.InferenceSettings;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderInvocation;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderReply;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Adapter for OpenAI, Groq and any endpoint with the OpenAI chat-completions contract. */
final class OpenAiCompatibleProviderAdapter implements AiProviderAdapter {
  private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(true, true, true, true, true, true, false, true);
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  OpenAiCompatibleProviderAdapter(ObjectMapper json) { this.json = json; }

  @Override public ProviderDescriptor descriptor() {
    return new ProviderDescriptor("openai-compatible", "OpenAI compatible", "1",
        List.of(new CredentialField("baseUrl", "URL base", false, true, "https://api.openai.com"),
            new CredentialField("apiKey", "API key", true, true, "Nunca se devuelve al cliente")), CAPABILITIES);
  }

  @Override public void validate(ProviderCredentialMaterial credential) {
    String baseUrl = credential.configuration("baseUrl");
    if (baseUrl == null || baseUrl.isBlank() || !"https".equalsIgnoreCase(URI.create(baseUrl).getScheme()))
      throw new ProviderException("INVALID_CONFIGURATION", "baseUrl debe ser una URL HTTPS");
    if (credential.secret("apiKey") == null || credential.secret("apiKey").isBlank())
      throw new ProviderException("INVALID_CREDENTIAL", "apiKey es obligatoria");
  }

  @Override public List<ModelDescriptor> discoverModels(ProviderCredentialMaterial credential) {
    validate(credential);
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(credential) + "/models"))
          .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + credential.secret("apiKey")).GET().build();
      JsonNode values = json.readTree(send(request)).path("data");
      return values.findValuesAsText("id").stream().filter(value -> !value.isBlank()).sorted()
          .map(value -> new ModelDescriptor(value, value, null, CAPABILITIES, Map.of())).toList();
    } catch (ProviderException exception) { throw exception; }
      catch (Exception exception) { throw new ProviderException("MODEL_DISCOVERY_FAILED", "No se pudieron descubrir modelos", exception); }
  }

  @Override public ProviderReply invoke(ProviderCredentialMaterial credential, ProviderInvocation invocation) {
    validate(credential);
    try {
      InferenceSettings settings = invocation.settings();
      var builder = OpenAiChatModel.builder().baseUrl(baseUrl(credential)).apiKey(credential.secret("apiKey"))
          .modelName(invocation.modelId()).timeout(invocation.timeout()).maxCompletionTokens(settings.maxOutputTokens())
          .logRequests(false).logResponses(false);
      if (settings.temperature() != null) builder.temperature(settings.temperature());
      if (settings.topP() != null) builder.topP(settings.topP());
      if (settings.seed() != null) builder.seed(Math.toIntExact(settings.seed()));
      return new ProviderReply(builder.build().chat(invocation.prompt()), 0, 0, null);
    } catch (ProviderException exception) { throw exception; }
      catch (Exception exception) { throw classifyInvocationFailure(exception); }
  }

  ProviderException classifyInvocationFailure(Exception exception) {
    if (exception instanceof dev.langchain4j.exception.TimeoutException
        || exception instanceof HttpTimeoutException
        || exception instanceof java.util.concurrent.TimeoutException) {
      return new ProviderException("PROVIDER_TIMEOUT", "El proveedor no respondió antes del timeout", exception);
    }
    if (exception instanceof AuthenticationException) {
      return new ProviderException("PROVIDER_AUTHENTICATION_FAILED", "El proveedor rechazó la credencial", exception);
    }
    if (exception instanceof ModelNotFoundException) {
      return new ProviderException("PROVIDER_MODEL_NOT_FOUND", "El modelo no está disponible en el proveedor", exception);
    }
    if (exception instanceof InvalidRequestException) {
      return new ProviderException("PROVIDER_INVALID_REQUEST", "El proveedor rechazó el pedido o el modelo", exception);
    }
    if (exception instanceof RateLimitException) {
      return new ProviderException("PROVIDER_RATE_LIMITED", "El proveedor aplicó límite de uso", exception);
    }
    if (exception instanceof InternalServerException || exception instanceof UnresolvedModelServerException) {
      return new ProviderException("PROVIDER_UNAVAILABLE", "El proveedor no está disponible", exception);
    }
    if (exception instanceof HttpException http) {
      return httpFailure(http.statusCode(), exception);
    }
    if (exception instanceof IOException) {
      return new ProviderException("PROVIDER_CONNECTION_FAILED", "No se pudo conectar con el proveedor", exception);
    }
    return new ProviderException("PROVIDER_INVOCATION_FAILED", "El proveedor OpenAI compatible no pudo responder", exception);
  }

  private String baseUrl(ProviderCredentialMaterial credential) {
    String value = credential.configuration("baseUrl").replaceFirst("/+$", "");
    return value.endsWith("/v1") ? value : value + "/v1";
  }

  private String send(HttpRequest request) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw httpFailure(response.statusCode(), null);
      return response.body();
    } catch (ProviderException exception) { throw exception; }
      catch (HttpTimeoutException exception) { throw classifyInvocationFailure(exception); }
      catch (IOException exception) { throw classifyInvocationFailure(exception); }
      catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new ProviderException("PROVIDER_INTERRUPTED", "La comunicación con el proveedor fue interrumpida", exception);
      }
  }

  private ProviderException httpFailure(int statusCode, Throwable cause) {
    String code = switch (statusCode) {
      case 401, 403 -> "PROVIDER_AUTHENTICATION_FAILED";
      case 400 -> "PROVIDER_INVALID_REQUEST";
      case 404 -> "PROVIDER_HTTP_404";
      case 408, 504 -> "PROVIDER_TIMEOUT";
      case 429 -> "PROVIDER_RATE_LIMITED";
      default -> statusCode >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_HTTP_" + statusCode;
    };
    String message = switch (code) {
      case "PROVIDER_AUTHENTICATION_FAILED" -> "El proveedor rechazó la credencial";
      case "PROVIDER_INVALID_REQUEST" -> "El proveedor rechazó el pedido o el modelo";
      case "PROVIDER_MODEL_NOT_FOUND" -> "El modelo no está disponible en el proveedor";
      case "PROVIDER_TIMEOUT" -> "El proveedor no respondió antes del timeout";
      case "PROVIDER_RATE_LIMITED" -> "El proveedor aplicó límite de uso";
      case "PROVIDER_UNAVAILABLE" -> "El proveedor no está disponible";
      default -> "El proveedor respondió HTTP " + statusCode;
    };
    return cause == null ? new ProviderException(code, message) : new ProviderException(code, message, cause);
  }
}
