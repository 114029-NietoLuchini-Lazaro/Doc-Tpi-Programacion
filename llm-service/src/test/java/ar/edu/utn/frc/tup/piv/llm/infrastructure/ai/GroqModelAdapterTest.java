package ar.edu.utn.frc.tup.piv.llm.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelResponseSchema;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias y escenarios BDD de {@link GroqModelAdapter} (LLM-EP02-H02).
 */
class GroqModelAdapterTest {

  @Test
  void throwsExplicitErrorWhenApiKeyIsMissing_Scenario3() {
    // Escenario BDD 3 / CA5: Falla explícita si falta la API Key
    var adapter = new GroqModelAdapter(null, null, null, null);
    var request = new ModelInvocationRequest(
        ModelFunction.TUTOR, "system prompt", "¿Cómo ordeno una lista?", Duration.ofSeconds(5));

    assertThatThrownBy(() -> adapter.invoke(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GROQ_API_KEY");
  }

  @Test
  void throwsExplicitErrorWhenApiKeyIsBlank() {
    var adapter = new GroqModelAdapter("   ", null, null, null);
    var request = new ModelInvocationRequest(
        ModelFunction.TUTOR, "system prompt", "pregunta", Duration.ofSeconds(5));

    assertThatThrownBy(() -> adapter.invoke(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GROQ_API_KEY");
  }

  @Test
  void generatesRealResponseThroughPort_Scenario1() {
    // Escenario BDD 1 / CA1: Invocación dentro de lo esperado
    ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
    when(mockModel.generate(anyList()))
        .thenReturn(Response.from(AiMessage.from("Respuesta real socrática generada por LLaMA")));

    var adapter = new GroqModelAdapter("gsk_test_key", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", mockModel);
    var request = new ModelInvocationRequest(
        ModelFunction.TUTOR, "Sos un tutor socrático", "¿Cómo funciona QuickSort?", Duration.ofSeconds(5));

    var result = adapter.invoke(request);

    assertThat(result.text()).isEqualTo("Respuesta real socrática generada por LLaMA");
    assertThat(result.provider()).isEqualTo("groq");
    assertThat(result.model()).isEqualTo("llama-3.3-70b-versatile");
  }

  @Test
  void theEvaluatorResponseWrappedInACodeFenceReachesTheSchemaAsBareJson() {
    String scores = "{\"autonomy\":80,\"clarity\":70,\"progression\":60,\"compliance\":90,\"efficiency\":50}";
    ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
    when(mockModel.generate(anyList()))
        .thenReturn(Response.from(AiMessage.from("Esta es mi evaluación:\n```json\n" + scores + "\n```")));
    var adapter = new GroqModelAdapter("gsk_test_key", null, "openai/gpt-oss-20b", mockModel);
    var request = new ModelInvocationRequest(ModelFunction.EVALUATOR, "rúbrica", "transcripción", Duration.ofSeconds(5));

    var result = adapter.invoke(request);

    assertThat(result.text()).isEqualTo(scores);
    new ModelResponseSchema().validate(ModelFunction.EVALUATOR, result.text());
  }

  @Test
  void theTutorResponseIsNeverRewrittenEvenIfItContainsBracesOrFences() {
    String reply = "Probá pensar el caso base:\n```java\nif (n == 0) { return 1; }\n```";
    ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
    when(mockModel.generate(anyList())).thenReturn(Response.from(AiMessage.from(reply)));
    var adapter = new GroqModelAdapter("gsk_test_key", null, "openai/gpt-oss-20b", mockModel);
    var request = new ModelInvocationRequest(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(5));

    assertThat(adapter.invoke(request).text()).isEqualTo(reply);
  }

  @Test
  void wrapsProviderExceptionControlled_Scenario2TransportError() {
    ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
    when(mockModel.generate(anyList()))
        .thenThrow(new RuntimeException("Rate limit 429 de Groq"));

    var adapter = new GroqModelAdapter("gsk_test_key", null, null, mockModel);
    var request = new ModelInvocationRequest(
        ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(5));

    assertThatThrownBy(() -> adapter.invoke(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("groq")
        .hasMessageContaining("Rate limit 429");
  }

  @Test
  void exposesProviderAndModelMetadata() {
    var adapter = new GroqModelAdapter("gsk_key", null, "llama-3.3-70b-versatile", null);

    assertThat(adapter.provider()).isEqualTo("groq");
    assertThat(adapter.model()).isEqualTo("llama-3.3-70b-versatile");
  }
}
