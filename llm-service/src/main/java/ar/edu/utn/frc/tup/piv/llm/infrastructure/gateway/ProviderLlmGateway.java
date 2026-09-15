package ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ar.edu.utn.frc.tup.piv.llm.application.CalibrationInferencePolicy.Settings;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * Internal AI Gateway. No web controller or application service talks to a provider directly.
 */
@Component
public class ProviderLlmGateway {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;

    public ProviderLlmGateway(ObjectMapper json) {
        this.json = json;
    }

    public List<String> listModels(Provider provider, String baseUrl, String secret) {
        var request = HttpRequest.newBuilder(modelsUri(provider, baseUrl)).timeout(Duration.ofSeconds(20))
                .headers(authHeader(provider, secret)).GET().build();
        var body = send(request);
        JsonNode root = read(body);
        List<String> models = new ArrayList<>();
        JsonNode values = provider == Provider.GEMINI ? root.path("models") : root.path("data");
        values.forEach(item -> models.add(provider == Provider.GEMINI
                ? item.path("name").asText().replaceFirst("^models/", "") : item.path("id").asText()));
        return models.stream().filter(value -> !value.isBlank()).sorted().toList();
    }

    public Reply chat(Provider provider, String baseUrl, String secret, String model, String message) {
        return chat(provider, baseUrl, secret, model, message, null);
    }

    /**
     * Runs inference through LangChain4j. The remaining JDK client is intentionally restricted
     * to provider catalog discovery and the legacy SSE reader; no model response is parsed here.
     */
    public Reply chat(Provider provider, String baseUrl, String secret, String model, String message, Settings settings) {
        try {
            return new Reply(model(provider, baseUrl, secret, model, settings).chat(message), 0, 0, null);
        } catch (Exception exception) {
            throw new IllegalStateException("El proveedor no pudo responder la prueba", exception);
        }
    }

    public Reply streamChat(Provider provider, String baseUrl, String secret, String model, String message, Consumer<String> onDelta) {
        Reply reply = chat(provider, baseUrl, secret, model, message);
        onDelta.accept(reply.text());
        return reply;
    }

    public void validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("La URL base debe ser HTTPS válida");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress())
                    throw new IllegalArgumentException("La URL base no puede apuntar a una red privada");
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No se pudo validar la URL base");
        }
    }

    private URI modelsUri(Provider provider, String baseUrl) {
        return switch (provider) {
            case OPENAI_COMPATIBLE -> URI.create(normalize(baseUrl) + "/models");
            case ANTHROPIC -> URI.create("https://api.anthropic.com/v1/models");
            case GEMINI -> URI.create("https://generativelanguage.googleapis.com/v1beta/models");
        };
    }

    private String[] authHeader(Provider provider, String secret) {
        return switch (provider) {
            case OPENAI_COMPATIBLE -> new String[]{"Authorization", "Bearer " + secret};
            case ANTHROPIC -> new String[]{"x-api-key", secret, "anthropic-version", "2023-06-01"};
            case GEMINI -> new String[]{"x-goog-api-key", secret};
        };
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("El proveedor respondió HTTP " + response.statusCode());
            return response.body();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo conectar con el proveedor", exception);
        }
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Respuesta de proveedor inválida", exception);
        }
    }

    private String normalize(String baseUrl) {
        return baseUrl.replaceFirst("/+$", "") + "/v1".replace(baseUrl.endsWith("/v1") ? "/v1" : "", "");
    }

    private ChatModel model(Provider provider, String baseUrl, String secret, String modelId, Settings settings) {
        Duration timeout = Duration.ofSeconds(90);
        int maxTokens = settings == null ? 512 : settings.maxOutputTokens();
        return switch (provider) {
            case OPENAI_COMPATIBLE -> {
                var builder = OpenAiChatModel.builder().baseUrl(normalize(baseUrl)).apiKey(secret).modelName(modelId)
                    .maxCompletionTokens(maxTokens).timeout(timeout).logRequests(false).logResponses(false);
                if (settings != null) { builder.temperature(settings.temperature()).topP(settings.topP()).seed(Math.toIntExact(settings.seed())); }
                yield builder.build();
            }
            case ANTHROPIC -> {
                var builder = AnthropicChatModel.builder().apiKey(secret).modelName(modelId).maxTokens(maxTokens)
                    .timeout(timeout).logRequests(false).logResponses(false);
                if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
                if (settings != null) { builder.temperature(settings.temperature()).topP(settings.topP()).topK(settings.topK()); }
                yield builder.build();
            }
            case GEMINI -> {
                var builder = GoogleAiGeminiChatModel.builder().apiKey(secret).modelName(modelId).maxOutputTokens(maxTokens)
                    .timeout(timeout).logRequests(false).logResponses(false);
                if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
                if (settings != null) { builder.temperature(settings.temperature()).topP(settings.topP()).topK(settings.topK()).seed(Math.toIntExact(settings.seed())); }
                yield builder.build();
            }
        };
    }

    public enum Provider {OPENAI_COMPATIBLE, ANTHROPIC, GEMINI}

    public record Reply(String text, int inputTokens, int outputTokens, String providerFingerprint) {
    }
}
