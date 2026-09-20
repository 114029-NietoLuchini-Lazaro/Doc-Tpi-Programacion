package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationUnavailableException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test void mapsValidationAndIdempotencyConflictsToExpectedProblemStatuses() {
    assertThat(handler.invalid(new IllegalArgumentException("inválido"), request).getStatus()).isEqualTo(422);
    assertThat(handler.conflict(new IllegalStateException("en curso"), request).getStatus()).isEqualTo(409);
  }

  @Test void mapsModelProviderFailuresToServiceUnavailable() {
    assertThat(handler.modelUnavailable(new ModelInvocationUnavailableException("provider"), request).getStatus())
        .isEqualTo(503);
    var providerProblem = handler.providerUnavailable(new ProviderException("PROVIDER_TIMEOUT", "lento"), request);
    assertThat(providerProblem.getStatus()).isEqualTo(503);
    assertThat(providerProblem.getProperties()).containsEntry("provider_code", "PROVIDER_TIMEOUT");
  }

  @Test void mapsProviderConfigurationAndRequestFailuresToUnprocessableEntity() {
    assertThat(handler.providerUnavailable(new ProviderException("INVALID_CREDENTIAL", "credencial"), request).getStatus())
        .isEqualTo(422);
    assertThat(handler.providerUnavailable(new ProviderException("PROVIDER_MODEL_NOT_FOUND", "modelo"), request).getStatus())
        .isEqualTo(422);
    assertThat(handler.providerUnavailable(new ProviderException("PROVIDER_HTTP_404", "not found"), request).getStatus())
        .isEqualTo(422);
  }
}
