package ar.edu.utn.frc.tup.piv.llm.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelResponseSchema;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Los 3 escenarios BDD de `LLM-S01-H10`. */
class FakeModelAdapterTest {

  @Test
  void respondsWithSimulatedDataThroughThePort() {
    var adapter = new FakeModelAdapter(Duration.ZERO, false);
    var request = new ModelInvocationRequest(ModelFunction.TUTOR, "system", "¿Cómo ordeno una lista?", Duration.ofSeconds(1));

    var result = adapter.invoke(request);

    assertThat(result.text()).isNotBlank();
    assertThat(result.provider()).isEqualTo("fake");
    assertThat(result.model()).isEqualTo("fake-socratic-v1");
  }

  @Test
  void canBeForcedToReturnAnOutOfSchemaResponse() {
    var adapter = new FakeModelAdapter(Duration.ZERO, true);
    var request = new ModelInvocationRequest(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1));

    var result = adapter.invoke(request);

    assertThat(result.text()).isBlank();
  }

  @Test
  void respondsWithAValidDeterministicScoreJsonForTheEvaluator() {
    var adapter = new FakeModelAdapter(Duration.ZERO, false);
    var request = new ModelInvocationRequest(ModelFunction.EVALUATOR, "system", "transcripción de prueba", Duration.ofSeconds(1));

    var first = adapter.invoke(request);
    var second = adapter.invoke(request);

    new ModelResponseSchema().validate(ModelFunction.EVALUATOR, first.text());
    assertThat(first.text()).isEqualTo(second.text());
  }

  @Test
  void canSimulateADelayLongerThanTheCallersTimeout() {
    var adapter = new FakeModelAdapter(Duration.ofMillis(200), false);
    var request = new ModelInvocationRequest(ModelFunction.TUTOR, "system", "pregunta", Duration.ofMillis(50));

    long start = System.currentTimeMillis();
    adapter.invoke(request);
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed).isGreaterThanOrEqualTo(200);
  }

  @Test
  void theProviderPayloadKeepsSystemAndUserInSeparateMessages() throws Exception {
    String system = "Sos un tutor socrático. Nunca des la solución.";
    String user = "<mensaje_alumno>\n¿cómo sigo? \"con comillas\"\n</mensaje_alumno>";

    var payload = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(new FakeModelAdapter().buildJsonPayload("modelo", system, user));

    var messages = payload.path("messages");
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
    assertThat(messages.get(0).path("content").asText()).isEqualTo(system);
    assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
    assertThat(messages.get(1).path("content").asText()).isEqualTo(user);
    assertThat(messages.get(1).path("content").asText()).doesNotContain("socrático");
  }
}
