package ar.edu.utn.frc.tup.piv.llm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

class RagGatewayAuthorizationTest {
  private static final String TRUSTED_SERVICE = "practice-service";
  private static final String REQUIRED_SCOPE = "llm.rag.query";

  @Test
  void inWorkbenchModeAlwaysResolvesTheWorkbenchUserWithoutHeaders() {
    UUID workbenchUser = UUID.randomUUID();
    var authorization = new RagGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, true, workbenchUser);

    var actor = authorization.require(new HttpHeaders());

    assertThat(actor.serviceId()).isEqualTo("workbench");
    assertThat(actor.delegatedUserId()).isEqualTo(workbenchUser);
  }

  @Test
  void acceptsATrustedServiceWithTheRequiredScope() {
    var authorization = new RagGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    UUID delegatedUser = UUID.randomUUID();
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", "llm.tutor.interact llm.rag.query");
    headers.set("X-Delegated-User", delegatedUser.toString());
    headers.set("X-Request-Id", "req-1");

    var actor = authorization.require(headers);

    assertThat(actor.serviceId()).isEqualTo(TRUSTED_SERVICE);
    assertThat(actor.delegatedUserId()).isEqualTo(delegatedUser);
    assertThat(actor.requestId()).isEqualTo("req-1");
  }

  @Test
  void rejectsAnUntrustedServiceId() {
    var authorization = new RagGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", "another-service");
    headers.set("X-Service-Scopes", REQUIRED_SCOPE);
    headers.set("X-Delegated-User", UUID.randomUUID().toString());

    assertThatThrownBy(() -> authorization.require(headers)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsATrustedServiceWithoutTheRequiredScope() {
    var authorization = new RagGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", "llm.tutor.interact");
    headers.set("X-Delegated-User", UUID.randomUUID().toString());

    assertThatThrownBy(() -> authorization.require(headers)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsAMissingDelegatedUser() {
    var authorization = new RagGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", REQUIRED_SCOPE);

    assertThatThrownBy(() -> authorization.require(headers)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsANonUuidDelegatedUser() {
    var authorization = new RagGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", REQUIRED_SCOPE);
    headers.set("X-Delegated-User", "not-a-uuid");

    assertThatThrownBy(() -> authorization.require(headers)).isInstanceOf(ResponseStatusException.class);
  }
}
