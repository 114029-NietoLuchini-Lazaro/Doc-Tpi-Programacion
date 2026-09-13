package ar.edu.utn.frc.tup.piv.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidEmbeddingException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.FunctionModelConfigRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbeddingInvocationServiceTest {

  @Test
  void embedsWhenTheFunctionIsEnabled() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("fake", "fake-embedding-768", "1", true)));
    var adapter = mock(EmbeddingPort.class);
    when(adapter.embed("hola")).thenReturn(new EmbeddingResult(new float[768], "fake", "fake-embedding-768"));
    var service = new EmbeddingInvocationService(configs, adapter);

    var result = service.embed("hola", Duration.ofSeconds(1));

    assertThat(result.vector()).hasSize(768);
  }

  @Test
  void failsWhenTheFunctionHasNoModelAssigned() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING)).thenReturn(Optional.empty());
    var adapter = mock(EmbeddingPort.class);
    var service = new EmbeddingInvocationService(configs, adapter);

    assertThatThrownBy(() -> service.embed("hola", Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsWhenTheFunctionIsDisabled() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("fake", "fake-embedding-768", "1", false)));
    var adapter = mock(EmbeddingPort.class);
    var service = new EmbeddingInvocationService(configs, adapter);

    assertThatThrownBy(() -> service.embed("hola", Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsANullVectorForASingleEmbed() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("fake", "fake-embedding-768", "1", true)));
    var adapter = mock(EmbeddingPort.class);
    when(adapter.embed(any())).thenReturn(new EmbeddingResult(null, "fake", "fake-embedding-768"));
    var service = new EmbeddingInvocationService(configs, adapter);

    assertThatThrownBy(() -> service.embed("hola", Duration.ofSeconds(1))).isInstanceOf(InvalidEmbeddingException.class);
  }

  @Test
  void rejectsAVectorWithTheWrongDimension() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("fake", "fake-embedding-768", "1", true)));
    var adapter = mock(EmbeddingPort.class);
    when(adapter.embed(any())).thenReturn(new EmbeddingResult(new float[10], "fake", "fake-embedding-768"));
    var service = new EmbeddingInvocationService(configs, adapter);

    assertThatThrownBy(() -> service.embed("hola", Duration.ofSeconds(1))).isInstanceOf(InvalidEmbeddingException.class);
  }

  @Test
  void embedBatchToleratesIndividualNullVectors() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("fake", "fake-embedding-768", "1", true)));
    var adapter = mock(EmbeddingPort.class);
    when(adapter.embedBatch(any())).thenReturn(List.of(
        new EmbeddingResult(new float[768], "fake", "fake-embedding-768"),
        new EmbeddingResult(null, "fake", "fake-embedding-768")));
    var service = new EmbeddingInvocationService(configs, adapter);

    var results = service.embedBatch(List.of("a", "b"), Duration.ofSeconds(1));

    assertThat(results).hasSize(2);
    assertThat(results.get(1).vector()).isNull();
  }

  @Test
  void cutsTheCallWhenTheAdapterExceedsTheConfiguredTimeout() {
    var configs = mock(FunctionModelConfigRepository.class);
    when(configs.find(ModelFunction.EMBEDDING))
        .thenReturn(Optional.of(new FunctionModelConfigRepository.Config("fake", "fake-embedding-768", "1", true)));
    var adapter = mock(EmbeddingPort.class);
    when(adapter.embed(any())).thenAnswer(invocation -> {
      Thread.sleep(300);
      return new EmbeddingResult(new float[768], "fake", "fake-embedding-768");
    });
    var service = new EmbeddingInvocationService(configs, adapter);

    assertThatThrownBy(() -> service.embed("hola", Duration.ofMillis(50))).isInstanceOf(EmbeddingTimeoutException.class);
  }

  @Test
  void embedBatchOfAnEmptyListReturnsEmptyWithoutCallingTheAdapter() {
    var configs = mock(FunctionModelConfigRepository.class);
    var adapter = mock(EmbeddingPort.class);
    var service = new EmbeddingInvocationService(configs, adapter);

    assertThat(service.embedBatch(List.of(), Duration.ofSeconds(1))).isEmpty();
  }
}
