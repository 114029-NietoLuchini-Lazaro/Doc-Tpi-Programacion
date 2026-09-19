package ar.edu.utn.frc.tup.piv.llm.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.application.ModelInvocationService;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.FunctionModelConfigRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba de integración en vivo contra Groq utilizando la clave configurada en .env o entorno.
 */
class GroqModelAdapterLiveTest {

  private String apiKey;
  private String model;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    apiKey = System.getenv("GROQ_API_KEY");
    model = System.getenv("GROQ_MODEL");
    baseUrl = System.getenv("GROQ_BASE_URL");

    if (apiKey == null || apiKey.isBlank()) {
      // Intentar leer desde .env local
      Path envPath = Paths.get(".env");
      if (!Files.exists(envPath)) {
        envPath = Paths.get("llm-service", ".env");
      }
      if (Files.exists(envPath)) {
        try {
          List<String> lines = Files.readAllLines(envPath);
          for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("GROQ_API_KEY=")) {
              apiKey = trimmed.substring("GROQ_API_KEY=".length()).trim();
            } else if (trimmed.startsWith("GROQ_MODEL=")) {
              model = trimmed.substring("GROQ_MODEL=".length()).trim();
            } else if (trimmed.startsWith("GROQ_BASE_URL=")) {
              baseUrl = trimmed.substring("GROQ_BASE_URL=".length()).trim();
            }
          }
        } catch (IOException ignored) {
        }
      }
    }

    if (model == null || model.isBlank()) {
      model = GroqModelAdapter.DEFAULT_MODEL;
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = GroqModelAdapter.DEFAULT_BASE_URL;
    }

    Assumptions.assumeTrue(
        apiKey != null && !apiKey.isBlank(),
        "Se omite la prueba en vivo porque no se encontró GROQ_API_KEY configurada.");
  }

  @Test
  void invokesRealGroqProviderSuccessfullyThroughPort() {
    var adapter = new GroqModelAdapter(apiKey, baseUrl, model);
    var request = new ModelInvocationRequest(
        ModelFunction.TUTOR,
        "Eres un tutor socrático para estudiantes de programación. Responde brevemente con una pregunta guía.",
        "¿Qué es una lista enlazada y cuándo me conviene usarla?",
        Duration.ofSeconds(15));

    ModelInvocationResult result = adapter.invoke(request);

    System.out.println("=== RESPUESTA EN VIVO DE GROQ ===");
    System.out.println("Proveedor: " + result.provider());
    System.out.println("Modelo: " + result.model());
    System.out.println("Texto generado:\n" + result.text());
    System.out.println("==================================");

    assertThat(result.text()).isNotBlank();
    assertThat(result.provider()).isEqualTo("groq");
    assertThat(result.model()).isEqualTo(model);
  }

  @Test
  void invokesRealGroqThroughModelInvocationServiceWithDynamicRouting() {
    var realGroqAdapter = new GroqModelAdapter(apiKey, baseUrl, model);
    var fakeAdapter = new FakeModelAdapter();

    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("groq", model, "1", true)));

    var service = new ModelInvocationService(configs, List.of(fakeAdapter, realGroqAdapter));

    ModelInvocationResult result = service.invoke(
        ModelFunction.TUTOR,
        "Sos un tutor académico.",
        "Explicame en una frase qué es recursión.",
        Duration.ofSeconds(15));

    System.out.println("=== RESPUESTA DE MODEL_INVOCATION_SERVICE (GROQ) ===");
    System.out.println("Proveedor: " + result.provider());
    System.out.println("Texto: " + result.text());
    System.out.println("====================================================");

    assertThat(result.text()).isNotBlank();
    assertThat(result.provider()).isEqualTo("groq");
  }
}
