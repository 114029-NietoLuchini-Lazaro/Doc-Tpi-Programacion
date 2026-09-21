package ar.edu.utn.frc.tup.piv.llm.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelResponseSchemaTest {
  private final ModelResponseSchema schema = new ModelResponseSchema();

  @Test
  void acceptsAValidFiveDimensionScoreJson() {
    assertThatCode(() -> schema.validate(ModelFunction.EVALUATOR,
        "{\"autonomy\":70,\"clarity\":65,\"progression\":80,\"compliance\":90,\"efficiency\":60}"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAScoreJsonMissingADimension() {
    assertThatThrownBy(() -> schema.validate(ModelFunction.EVALUATOR,
        "{\"autonomy\":70,\"clarity\":65,\"progression\":80,\"compliance\":90}"))
        .isInstanceOf(InvalidModelResponseException.class);
  }

  @Test
  void rejectsAScoreOutOfThe0To100Range() {
    assertThatThrownBy(() -> schema.validate(ModelFunction.EVALUATOR,
        "{\"autonomy\":170,\"clarity\":65,\"progression\":80,\"compliance\":90,\"efficiency\":60}"))
        .isInstanceOf(InvalidModelResponseException.class);
  }

  @Test
  void rejectsTextThatIsNotJsonForTheEvaluator() {
    assertThatThrownBy(() -> schema.validate(ModelFunction.EVALUATOR, "no es json"))
        .isInstanceOf(InvalidModelResponseException.class);
  }

  @Test
  void tutorStillAcceptsPlainProse() {
    assertThat(schema).isNotNull();
    assertThatCode(() -> schema.validate(ModelFunction.TUTOR, "una pista socrática")).doesNotThrowAnyException();
  }
}
