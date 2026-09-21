package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.application.service.TutorInteractionService;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.TutorGatewayAuthorization;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TutorInteractionControllerTest {

  @Test
  void authorizesBeforeCallingTheService() {
    var service = mock(TutorInteractionService.class);
    var authorization = mock(TutorGatewayAuthorization.class);
    var actor = new CallerIdentity("practice-service", UUID.randomUUID(), null, null);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    var expected = new TutorInteractionService.Response("hola", "completed", UUID.randomUUID());
    when(service.respond(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actor)))
        .thenReturn(expected);
    var controller = new TutorInteractionController(service, authorization);
    var body = new TutorInteractionController.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hola", "low", null);

    var response = controller.create(body, UUID.randomUUID(), headers);

    assertThat(response).isEqualTo(expected);
    var order = Mockito.inOrder(authorization, service);
    order.verify(authorization).require(headers);
    order.verify(service).respond(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actor));
  }

  @Test
  void rejectsInvalidAuthenticationBeforeReachingTheService() {
    var service = mock(TutorInteractionService.class);
    var authorization = new TutorGatewayAuthorization("practice-service", "llm.tutor.interact", false, UUID.randomUUID());
    var controller = new TutorInteractionController(service, authorization);
    var body = new TutorInteractionController.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hola", "low", null);
    var headers = new HttpHeaders();
    headers.add("X-Service-Id", "intruso");
    headers.add("X-Service-Scopes", "llm.tutor.interact");
    headers.add("X-Delegated-User", UUID.randomUUID().toString());

    assertThatThrownBy(() -> controller.create(body, UUID.randomUUID(), headers))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    Mockito.verifyNoInteractions(service);
  }

  @Test
  void rejectsMissingDelegatedUserBeforeReachingTheService() {
    var service = mock(TutorInteractionService.class);
    var authorization = new TutorGatewayAuthorization("practice-service", "llm.tutor.interact", false, UUID.randomUUID());
    var controller = new TutorInteractionController(service, authorization);
    var body = new TutorInteractionController.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hola", "low", null);
    var headers = new HttpHeaders();
    headers.add("X-Service-Id", "practice-service");
    headers.add("X-Service-Scopes", "llm.tutor.interact");

    assertThatThrownBy(() -> controller.create(body, UUID.randomUUID(), headers))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    Mockito.verifyNoInteractions(service);
  }

  @Test
  void rejectsARequestWithoutARiskLevel() {
    var service = mock(TutorInteractionService.class);
    var authorization = mock(TutorGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(new CallerIdentity("practice-service", UUID.randomUUID(), null, null));
    var controller = new TutorInteractionController(service, authorization);
    var body = new TutorInteractionController.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hola", null, null);

    assertThatThrownBy(() -> controller.create(body, UUID.randomUUID(), headers)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABlankMessage() {
    var service = mock(TutorInteractionService.class);
    var authorization = mock(TutorGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(new CallerIdentity("practice-service", UUID.randomUUID(), null, null));
    var controller = new TutorInteractionController(service, authorization);
    var body = new TutorInteractionController.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "  ", "low", null);

    assertThatThrownBy(() -> controller.create(body, UUID.randomUUID(), headers)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsTheOptionalFieldsOfTheContractFromJsonAndIgnoresUnknownOnes() throws Exception {
    var json = """
        {"attemptId":"b1e2c3d4-0001-4a00-8000-000000000001","challengeId":"b1e2c3d4-0002-4a00-8000-000000000002",
         "courseCohortId":"b1e2c3d4-0003-4a00-8000-000000000003","learnerId":"b1e2c3d4-0004-4a00-8000-000000000004",
         "message":"hola","riskLevel":"medium","conversacionId":"b1e2c3d4-0005-4a00-8000-000000000005",
         "expectedSolution":"return 1;","campoNuevo":"se ignora"}
        """;
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    var body = mapper.readValue(json, TutorInteractionController.Request.class);

    assertThat(body.expectedSolution()).isEqualTo("return 1;");
    assertThat(body.conversacionId()).hasToString("b1e2c3d4-0005-4a00-8000-000000000005");
    assertThat(body.toString()).doesNotContain("return 1;");
  }
}
