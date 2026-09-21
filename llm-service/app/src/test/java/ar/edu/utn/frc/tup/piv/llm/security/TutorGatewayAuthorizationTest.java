package ar.edu.utn.frc.tup.piv.llm.security;

import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.TutorGatewayAuthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TutorGatewayAuthorizationTest {
  private static final String TRUSTED_SERVICE = "practice-service";
  private static final String REQUIRED_SCOPE = "llm.tutor.interact";

  @Test
  void inWorkbenchModeAlwaysResolvesTheWorkbenchUserWithoutHeaders() {
    UUID workbenchUser = UUID.randomUUID();
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, true, workbenchUser);

    var actor = authorization.require(new HttpHeaders());

    assertThat(actor.serviceId()).isEqualTo("workbench");
    assertThat(actor.delegatedUserId()).isEqualTo(workbenchUser);
  }

  @Test
  void acceptsATrustedServiceWithTheRequiredScope() {
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    UUID delegatedUser = UUID.randomUUID();
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", "llm.tutor.interact other.scope");
    headers.set("X-Delegated-User", delegatedUser.toString());
    headers.set("X-Request-Id", "req-tutor-1");

    var actor = authorization.require(headers);

    assertThat(actor.serviceId()).isEqualTo(TRUSTED_SERVICE);
    assertThat(actor.delegatedUserId()).isEqualTo(delegatedUser);
    assertThat(actor.requestId()).isEqualTo("req-tutor-1");
  }

  @Test
  void rejectsAnUntrustedServiceId() {
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", "untrusted-service");
    headers.set("X-Service-Scopes", REQUIRED_SCOPE);
    headers.set("X-Delegated-User", UUID.randomUUID().toString());

    assertThatThrownBy(() -> authorization.require(headers))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          var rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(rse.getReason()).isEqualTo("Falta el permiso requerido");
        });
  }

  @Test
  void rejectsATrustedServiceWithoutTheRequiredScope() {
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", "other.scope");
    headers.set("X-Delegated-User", UUID.randomUUID().toString());

    assertThatThrownBy(() -> authorization.require(headers))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          var rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(rse.getReason()).isEqualTo("Falta el permiso requerido");
        });
  }

  @Test
  void rejectsATrustedServiceWithNullScopes() {
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Delegated-User", UUID.randomUUID().toString());

    assertThatThrownBy(() -> authorization.require(headers))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          var rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(rse.getReason()).isEqualTo("Falta el permiso requerido");
        });
  }

  @Test
  void rejectsAMissingDelegatedUser() {
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", REQUIRED_SCOPE);

    assertThatThrownBy(() -> authorization.require(headers))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          var rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(rse.getReason()).isEqualTo("Identidad delegada ausente");
        });
  }

  @Test
  void rejectsANonUuidDelegatedUser() {
    var authorization = new TutorGatewayAuthorization(TRUSTED_SERVICE, REQUIRED_SCOPE, false, UUID.randomUUID());
    var headers = new HttpHeaders();
    headers.set("X-Service-Id", TRUSTED_SERVICE);
    headers.set("X-Service-Scopes", REQUIRED_SCOPE);
    headers.set("X-Delegated-User", "not-a-uuid");

    assertThatThrownBy(() -> authorization.require(headers))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          var rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(rse.getReason()).isEqualTo("Identidad delegada inválida");
        });
  }
}
