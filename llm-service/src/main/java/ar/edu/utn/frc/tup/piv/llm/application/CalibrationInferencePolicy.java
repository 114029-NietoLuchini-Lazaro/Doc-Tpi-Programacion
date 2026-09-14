package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway.ProviderLlmGateway.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Institutional, immutable inference policy used by calibration and future evaluation executors. */
@Component
public class CalibrationInferencePolicy {
  public Settings resolve(Provider provider, String baseUrl, long seed) {
    String host = baseUrl == null ? "" : baseUrl.toLowerCase();
    return switch (provider) {
      case GEMINI -> new Settings("v1", 0d, 1d, null, seed, true, true, 128, "GEMINI");
      case ANTHROPIC -> new Settings("v1", null, null, null, null, false, false, 128, "ANTHROPIC_LIMITED");
      case OPENAI_COMPATIBLE -> host.contains("api.openai.com") || host.contains("api.groq.com")
          ? new Settings("v1", 0d, 1d, null, seed, true, true, 128, "OPENAI_OR_GROQ")
          : new Settings("v1", 0d, null, null, null, false, false, 128, "OPENAI_COMPATIBLE_MINIMAL");
    };
  }
  public record Settings(String version, Double temperature, Double topP, Integer topK, Long seed,
                         boolean structuredJson, boolean seedSupported, int maxOutputTokens, String profile) {
    public Map<String, Object> auditView() {
      var values = new LinkedHashMap<String, Object>();
      values.put("version", version); values.put("profile", profile); values.put("temperature", temperature);
      values.put("topP", topP); values.put("topK", topK); values.put("seed", seed);
      values.put("structuredJson", structuredJson); values.put("seedSupported", seedSupported);
      values.put("maxOutputTokens", maxOutputTokens); return java.util.Collections.unmodifiableMap(values);
    }
  }
}
