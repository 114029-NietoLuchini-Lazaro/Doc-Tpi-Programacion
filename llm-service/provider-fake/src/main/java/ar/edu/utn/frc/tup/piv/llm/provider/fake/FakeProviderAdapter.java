package ar.edu.utn.frc.tup.piv.llm.provider.fake;

import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderInvocation;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderReply;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class FakeProviderAdapter implements AiProviderAdapter {
  static final String PROVIDER_KEY = "fake";
  static final String VALID_MODEL = "fake-socratic-v1";
  static final String INVALID_MODEL = "fake-invalid-v1";
  static final String SLOW_MODEL = "fake-slow-v1";
  private static final Duration DEFAULT_SLOW_DELAY = Duration.ofMillis(250);
  private static final ProviderCapabilities CAPABILITIES =
      new ProviderCapabilities(true, false, false, false, false, false, false, false);

  @Override
  public ProviderDescriptor descriptor() {
    return new ProviderDescriptor(PROVIDER_KEY, "Fake provider", "1", List.of(), CAPABILITIES);
  }

  @Override
  public void validate(ProviderCredentialMaterial credential) {
    // The fake deliberately ignores credentials and never contacts a real provider.
  }

  @Override
  public List<ModelDescriptor> discoverModels(ProviderCredentialMaterial credential) {
    return List.of(
        model(VALID_MODEL, "Fake Socratic response"),
        model(INVALID_MODEL, "Fake invalid response"),
        model(SLOW_MODEL, "Fake delayed response"));
  }

  @Override
  public ProviderReply invoke(ProviderCredentialMaterial credential, ProviderInvocation invocation) {
    return switch (invocation.modelId()) {
      case VALID_MODEL -> reply(validResponse(invocation.prompt()));
      case INVALID_MODEL -> reply("");
      case SLOW_MODEL -> {
        delay(invocation.timeout());
        yield reply(validResponse(invocation.prompt()));
      }
      default -> throw new ProviderException(
          "PROVIDER_MODEL_NOT_FOUND", "El modelo fake no existe: " + invocation.modelId());
    };
  }

  private ModelDescriptor model(String modelId, String displayName) {
    return new ModelDescriptor(modelId, displayName, "1", CAPABILITIES, Map.of());
  }

  private ProviderReply reply(String text) {
    return new ProviderReply(text, 0, 0, "fake-in-process");
  }

  private String validResponse(String prompt) {
    String excerpt = firstWords(userPrompt(prompt), 12);
    return "¿Qué estructura o patrón te ayudaría a resolver \"" + excerpt
        + "\" sin escribir todavía el código completo? Contame qué probaste hasta ahora.";
  }

  private String userPrompt(String prompt) {
    if (prompt == null || prompt.isBlank()) return "tu consulta";
    int separator = prompt.lastIndexOf("\n\n");
    return separator < 0 ? prompt : prompt.substring(separator + 2);
  }

  private String firstWords(String text, int count) {
    if (text == null || text.isBlank()) return "tu consulta";
    String[] words = text.trim().split("\\s+");
    return String.join(" ", Arrays.copyOf(words, Math.min(count, words.length)));
  }

  private void delay(Duration timeout) {
    Duration delay = timeout == null ? DEFAULT_SLOW_DELAY : timeout.plusMillis(100);
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ProviderException("PROVIDER_INTERRUPTED", "La demora del fake fue interrumpida", exception);
    }
  }
}
