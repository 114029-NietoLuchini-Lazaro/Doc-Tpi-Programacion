package ar.edu.utn.frc.tup.piv.llm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository.Case;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository.Deployment;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository.Execution;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository.Run;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.EvaluatorSkillRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.EvaluatorSkillRepository.EvaluatorSkill;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository.Credential;
import ar.edu.utn.frc.tup.piv.llm.application.CalibrationInferencePolicy;
import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderDescriptor;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderReply;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RealCalibrationExecutorTest {

  private CalibrationRunRepository runs;
  private ProviderCredentialRepository usage;
  private ProviderInvocationGateway gateway;
  private ProviderRegistry registry;
  private EvaluatorSkillRepository skillRepository;
  private final ObjectMapper json = new ObjectMapper();
  private final CalibrationInferencePolicy policy = new CalibrationInferencePolicy();
  private RealCalibrationExecutor executor;

  private final UUID courseId = UUID.randomUUID();
  private final UUID runId = UUID.randomUUID();
  private final UUID credentialId = UUID.randomUUID();
  private final UUID deploymentId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    runs = mock(CalibrationRunRepository.class);
    usage = mock(ProviderCredentialRepository.class);
    gateway = mock(ProviderInvocationGateway.class);
    registry = mock(ProviderRegistry.class);
    skillRepository = mock(EvaluatorSkillRepository.class);

    executor = new RealCalibrationExecutor(runs, usage, gateway, registry, json, policy, skillRepository);

    // Mock active credential
    Credential credential = new Credential(
        credentialId, "test-provider", "Key", Map.of(), new byte[0], new byte[0], "***", "ACTIVE", Instant.now());
    when(usage.get(credentialId)).thenReturn(Optional.of(credential));

    // Mock provider capabilities
    var adapter = mock(AiProviderAdapter.class);
    var descriptor = mock(ProviderDescriptor.class);
    when(descriptor.capabilities()).thenReturn(new ProviderCapabilities(false, false, true, false, false, false, false, false));
    when(adapter.descriptor()).thenReturn(descriptor);
    when(registry.required("test-provider")).thenReturn(adapter);
  }

  @Test
  void execute_defaultInstitutionalFlow_runsStandardPathAndDoesNotQuerySkills() {
    Run run = new Run(runId, courseId, "COURSE", "RUNNING", 0, UUID.randomUUID(), UUID.randomUUID(), deploymentId, null, null, "MANUAL", Instant.now(), null, null, null);
    Deployment deployment = new Deployment(deploymentId, credentialId, "test-provider", "gpt-4o-mini");
    Map<Dimension, Integer> weights = standardWeights();

    ArrayNode transcript = json.createArrayNode();
    ObjectNode context = json.createObjectNode();
    Map<Dimension, Integer> humanScores = new EnumMap<>(Dimension.class);
    for (Dimension d : Dimension.values()) humanScores.put(d, 80);

    Case c = new Case(caseId, transcript, context, humanScores);
    Execution execution = new Execution(run, deployment, weights, "Rúbrica default", List.of(c), 0L);
    when(runs.execution(runId)).thenReturn(execution);

    when(gateway.invoke(any(), eq("gpt-4o-mini"), anyString(), any(), any()))
        .thenReturn(new ProviderReply("{\"AUTONOMY\":80,\"CLARITY\":80,\"PROGRESSION\":80,\"COMPLIANCE\":80,\"EFFICIENCY\":80}", 100, 50, "fp-1"));

    executor.execute(runId);

    verifyNoInteractions(skillRepository);
    verify(runs).saveCase(eq(runId), eq(c), any(), eq(weights));
    verify(runs).finish(eq(runId), eq(true), any(BigDecimal.class), anyInt());
    verify(runs).refreshStability(runId);
  }

  @Test
  void execute_modularCustomFlow_injectsActiveSkillsAndUserPromptAndParsesDynamicScores() {
    Run run = new Run(runId, courseId, "COURSE", "RUNNING", 0, UUID.randomUUID(), UUID.randomUUID(), deploymentId, null, null, "MANUAL", Instant.now(), null, null, null);
    Deployment deployment = new Deployment(deploymentId, credentialId, "test-provider", "gpt-4o-mini");

    ArrayNode transcript = json.createArrayNode();
    ObjectNode context = json.createObjectNode();
    Map<String, Integer> dynamicHuman = Map.of("code_quality", 80, "test_runner", 90);
    Case c = new Case(caseId, transcript, context, Map.of(), dynamicHuman);

    List<String> dimensionKeys = List.of("code_quality", "test_runner");
    Map<String, Integer> dynamicWeights = Map.of("code_quality", 60, "test_runner", 40);

    Execution execution = new Execution(
        run, deployment, Map.of(), "Rúbrica modular", List.of(c), 0L,
        "MODULAR_CUSTOM", "Enfocarse en buenas prácticas de clean code",
        dimensionKeys, dynamicWeights
    );
    when(runs.execution(runId)).thenReturn(execution);

    EvaluatorSkill activeSkill = new EvaluatorSkill(
        "code_quality", "Revisor de Calidad", "Inspecciona código", "STATIC_ANALYSIS",
        "Evaluar legibilidad y modularidad estricta.", true
    );
    when(skillRepository.findActiveByCourse(courseId)).thenReturn(List.of(activeSkill));

    when(gateway.invoke(any(), eq("gpt-4o-mini"), anyString(), any(), any()))
        .thenReturn(new ProviderReply("```json\n{\"code_quality\": 85, \"test_runner\": 90}\n```", 120, 60, "fp-2"));

    executor.execute(runId);

    verify(skillRepository).findActiveByCourse(courseId);

    // Verify prompt content
    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(gateway).invoke(any(), eq("gpt-4o-mini"), promptCaptor.capture(), any(), any());
    String sentPrompt = promptCaptor.getValue();
    assertThat(sentPrompt).contains("INSTRUCCIONES DOCENTE:\nEnfocarse en buenas prácticas de clean code");
    assertThat(sentPrompt).contains("HABILIDADES TÉCNICAS ACTIVADAS PARA EL ANÁLISIS:");
    assertThat(sentPrompt).contains("[Revisor de Calidad]: Evaluar legibilidad y modularidad estricta.");
    assertThat(sentPrompt).contains("Rúbrica modular");

    // Verify saving case and finish
    verify(runs).saveCaseModular(eq(runId), eq(c), eq(Map.of("code_quality", 85, "test_runner", 90)), eq(dynamicWeights));
    verify(runs).finish(eq(runId), eq(true), any(BigDecimal.class), anyInt());
    verify(runs).refreshStability(runId);
  }

  @Test
  void execute_modularCustomFlow_handlesNoSkillsOrPromptCleanly() {
    Run run = new Run(runId, courseId, "COURSE", "RUNNING", 0, UUID.randomUUID(), UUID.randomUUID(), deploymentId, null, null, "MANUAL", Instant.now(), null, null, null);
    Deployment deployment = new Deployment(deploymentId, credentialId, "test-provider", "gpt-4o-mini");

    ArrayNode transcript = json.createArrayNode();
    ObjectNode context = json.createObjectNode();
    Case c = new Case(caseId, transcript, context, Map.of(), Map.of("autonomia", 80));

    Execution execution = new Execution(
        run, deployment, Map.of(), "Rúbrica", List.of(c), 0L,
        "MODULAR_CUSTOM", null, List.of("autonomia"), Map.of("autonomia", 100)
    );
    when(runs.execution(runId)).thenReturn(execution);
    when(skillRepository.findActiveByCourse(courseId)).thenReturn(List.of());

    when(gateway.invoke(any(), eq("gpt-4o-mini"), anyString(), any(), any()))
        .thenReturn(new ProviderReply("{\"autonomia\": 80}", 100, 50, null));

    executor.execute(runId);

    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(gateway).invoke(any(), eq("gpt-4o-mini"), promptCaptor.capture(), any(), any());
    String sentPrompt = promptCaptor.getValue();
    assertThat(sentPrompt).doesNotContain("INSTRUCCIONES DOCENTE");
    assertThat(sentPrompt).doesNotContain("HABILIDADES TÉCNICAS ACTIVADAS");
    verify(runs).finish(eq(runId), eq(true), any(BigDecimal.class), anyInt());
  }

  @Test
  void execute_failsRun_whenModelResponseIsMalformed() {
    Run run = new Run(runId, courseId, "COURSE", "RUNNING", 0, UUID.randomUUID(), UUID.randomUUID(), deploymentId, null, null, "MANUAL", Instant.now(), null, null, null);
    Deployment deployment = new Deployment(deploymentId, credentialId, "test-provider", "gpt-4o-mini");

    Case c = new Case(caseId, json.createArrayNode(), json.createObjectNode(), Map.of(), Map.of("code_quality", 80));
    Execution execution = new Execution(
        run, deployment, Map.of(), "Rúbrica", List.of(c), 0L,
        "MODULAR_CUSTOM", "", List.of("code_quality"), Map.of("code_quality", 100)
    );
    when(runs.execution(runId)).thenReturn(execution);
    when(skillRepository.findActiveByCourse(courseId)).thenReturn(List.of());

    when(gateway.invoke(any(), eq("gpt-4o-mini"), anyString(), any(), any()))
        .thenReturn(new ProviderReply("Not a valid json response", 50, 20, null));

    executor.execute(runId);

    verify(runs).fail(eq(runId), eq("MALFORMED_MODEL_RESPONSE"), anyString());
    verify(runs).refreshStability(runId);
  }

  @Test
  void parseScoresModular_validatesKeysAndNumberRanges() {
    List<String> keys = List.of("dim1", "dim2");

    Map<String, Integer> scores = executor.parseScoresModular("{\"dim1\": 75, \"dim2\": 100}", keys);
    assertThat(scores).containsEntry("dim1", 75).containsEntry("dim2", 100);

    assertThatThrownBy(() -> executor.parseScoresModular("{\"dim1\": 75}", keys))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> executor.parseScoresModular("{\"dim1\": -1, \"dim2\": 50}", keys))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> executor.parseScoresModular("{\"dim1\": 101, \"dim2\": 50}", keys))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> executor.parseScoresModular("{\"dim1\": \"not-a-number\", \"dim2\": 50}", keys))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Map<Dimension, Integer> standardWeights() {
    Map<Dimension, Integer> weights = new EnumMap<>(Dimension.class);
    weights.put(Dimension.AUTONOMY, 20);
    weights.put(Dimension.CLARITY, 20);
    weights.put(Dimension.PROGRESSION, 20);
    weights.put(Dimension.COMPLIANCE, 20);
    weights.put(Dimension.EFFICIENCY, 20);
    return weights;
  }
}
