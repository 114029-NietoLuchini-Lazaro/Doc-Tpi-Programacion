package ar.edu.utn.frc.tup.piv.llm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.application.ModelAssignmentService;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.FunctionModelConfig;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.security.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.security.GoldenSetAuthorization;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ModelAssignmentControllerTest {

  @Test
  void returnsTheAssignmentConfiguredForTheFunction() {
    var service = mock(ModelAssignmentService.class);
    var authorization = mock(GoldenSetAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(new CallerIdentity("admin-service", UUID.randomUUID(), null, null));
    when(service.find(ModelFunction.TUTOR))
        .thenReturn(Optional.of(new FunctionModelConfig("fake", "fake-socratic-v1", "1", true)));
    var controller = new ModelAssignmentController(service, authorization);

    var assignment = controller.get("tutor", headers);

    assertThat(assignment.provider()).isEqualTo("fake");
    assertThat(assignment.modelId()).isEqualTo("fake-socratic-v1");
  }

  @Test
  void rejectsAnUnknownFunction() {
    var service = mock(ModelAssignmentService.class);
    var authorization = mock(GoldenSetAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(new CallerIdentity("admin-service", UUID.randomUUID(), null, null));
    var controller = new ModelAssignmentController(service, authorization);

    assertThatThrownBy(() -> controller.get("no-existe", headers)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatesTheAssignmentAfterAuthorizing() {
    var service = mock(ModelAssignmentService.class);
    var authorization = mock(GoldenSetAuthorization.class);
    var actor = new CallerIdentity("admin-service", UUID.randomUUID(), null, null);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    var controller = new ModelAssignmentController(service, authorization);
    var body = new ModelAssignmentController.Assignment("openai", "gpt-x", "2024");

    var result = controller.put("tutor", body, UUID.randomUUID(), headers);

    assertThat(result).isEqualTo(body);
    verify(service).upsert(ModelFunction.TUTOR, "openai", "gpt-x", "2024", actor);
  }
}
