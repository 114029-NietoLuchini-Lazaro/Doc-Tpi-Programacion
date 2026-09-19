package ar.edu.utn.frc.tup.piv.llm.infrastructure.ai;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador de invocación para el proveedor real Groq (ADR-016, LLM-EP02-H02).
 * Utiliza {@code langchain4j-open-ai} conectado a la API de Groq sin capas de agentes,
 * preservando la interfaz {@link ModelInvocationPort}.
 */
@Component
public class GroqModelAdapter implements ModelInvocationPort {
  public static final String PROVIDER = "groq";
  public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";
  public static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

  private final String apiKey;
  private final String baseUrl;
  private final String modelName;
  private final ChatLanguageModel customChatModel;

  @org.springframework.beans.factory.annotation.Autowired
  public GroqModelAdapter(
      @Value("${GROQ_API_KEY:${llm.provider.groq.api-key:}}") String apiKey,
      @Value("${GROQ_BASE_URL:${llm.provider.groq.base-url:https://api.groq.com/openai/v1}}") String baseUrl,
      @Value("${GROQ_MODEL:${llm.provider.groq.model:openai/gpt-oss-20b}}") String modelName) {
    this(apiKey, baseUrl, modelName, null);
  }

  GroqModelAdapter(String apiKey, String baseUrl, String modelName, ChatLanguageModel customChatModel) {
    this.apiKey = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : null;
    this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_BASE_URL;
    this.modelName = (modelName != null && !modelName.isBlank()) ? modelName.trim() : DEFAULT_MODEL;
    this.customChatModel = customChatModel;
  }

  @Override
  public ModelInvocationResult invoke(ModelInvocationRequest request) {
    if (apiKey == null) {
      throw new IllegalStateException(
          "No se configuró la variable de entorno GROQ_API_KEY para el proveedor '" + PROVIDER + "'");
    }

    ChatLanguageModel chatModel = customChatModel != null
        ? customChatModel
        : buildChatModel(request.timeout());

    List<ChatMessage> messages = new ArrayList<>();
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      messages.add(SystemMessage.from(request.systemPrompt()));
    }
    messages.add(UserMessage.from(request.userPrompt()));

    try {
      Response<AiMessage> response = chatModel.generate(messages);
      String responseText = response != null && response.content() != null ? response.content().text() : "";
      var usage = response != null ? response.tokenUsage() : null;
      return new ModelInvocationResult(responseText, PROVIDER, modelName,
          usage != null ? usage.inputTokenCount() : null, usage != null ? usage.outputTokenCount() : null);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Fallo en la comunicación con el proveedor '" + PROVIDER + "': " + exception.getMessage(), exception);
    }
  }

  private ChatLanguageModel buildChatModel(Duration timeout) {
    return OpenAiChatModel.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .modelName(modelName)
        .timeout(timeout != null ? timeout : Duration.ofSeconds(15))
        .maxRetries(0)
        .temperature(0.3)
        .build();
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public String model() {
    return modelName;
  }
}
