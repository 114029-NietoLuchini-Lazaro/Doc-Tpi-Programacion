package ar.edu.utn.frc.tup.piv.llm.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCredentialMaterial;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.ModelNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderAdapterTest {
  private final OpenAiCompatibleProviderAdapter adapter = new OpenAiCompatibleProviderAdapter(new ObjectMapper());

  @Test
  void classifiesSdkFailuresWithoutParsingProviderMessages() {
    assertThat(adapter.classifyInvocationFailure(new dev.langchain4j.exception.TimeoutException("slow")).code())
        .isEqualTo("PROVIDER_TIMEOUT");
    assertThat(adapter.classifyInvocationFailure(new AuthenticationException("bad key")).code())
        .isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
    assertThat(adapter.classifyInvocationFailure(new ModelNotFoundException("missing model")).code())
        .isEqualTo("PROVIDER_MODEL_NOT_FOUND");
    assertThat(adapter.classifyInvocationFailure(new HttpException(404, "missing model")).code())
        .isEqualTo("PROVIDER_HTTP_404");
  }

  @Test
  void validateRejectsMissingCredentialsAsProviderFailure() {
    var material = new ProviderCredentialMaterial("openai-compatible",
        Map.of("baseUrl", "https://api.groq.com/openai/v1"), Map.of("apiKey", " "));

    assertThatThrownBy(() -> adapter.validate(material))
        .isInstanceOf(ProviderException.class)
        .extracting("code")
        .isEqualTo("INVALID_CREDENTIAL");
  }
}
