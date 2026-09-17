package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.domain.calibration.CalibrationRun;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.AuditRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.CalibrationReproducibilityRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.CalibrationRunRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.security.CallerIdentity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalibrationRunService {
  private final CalibrationRunRepository runs;
  private final CalibrationReproducibilityRepository artifacts;
  private final AuditRepository audit;
  private final ProviderCredentialRepository models;

  @Autowired
  public CalibrationRunService(CalibrationRunRepository runs,
      CalibrationReproducibilityRepository artifacts, AuditRepository audit,
      ProviderCredentialRepository models) {
    this.runs = runs;
    this.artifacts = artifacts;
    this.audit = audit;
    this.models = models;
  }

  public CalibrationRunService(CalibrationRunRepository runs,
      CalibrationReproducibilityRepository artifacts, AuditRepository audit) {
    this(runs, artifacts, audit, null);
  }

  @Transactional
  public CalibrationRun enqueue(UUID courseId, UUID rubricVersionId, UUID goldenSetVersionId,
      UUID modelDeploymentId, UUID idempotencyKey, CallerIdentity actor) {
    UUID target = modelDeploymentId;
    if (target == null && models != null) {
      target = models.calibrationTarget()
          .map(ProviderCredentialRepository.Deployment::id)
          .orElseThrow(() -> new IllegalStateException("El admin debe seleccionar un modelo candidato para calibrar"));
    }
    var run = runs.create(courseId, rubricVersionId, goldenSetVersionId, target,
        idempotencyKey, actor.delegatedUserId());
    artifacts.snapshot(run.id(), JsonNodeFactory.instance.objectNode(), "");
    audit.record("calibration.queued", "calibration-run", run.id(), actor,
        "{\"courseId\":\"" + courseId + "\",\"rubricVersionId\":\"" + rubricVersionId
            + "\",\"goldenSetVersionId\":\"" + goldenSetVersionId
            + "\",\"modelDeploymentId\":\"" + target + "\"}");
    return run;
  }

  @Transactional(readOnly = true)
  public CalibrationRun get(UUID courseId, UUID runId) {
    return runs.find(courseId, runId)
        .orElseThrow(() -> new IllegalStateException("La calibración no existe en el curso"));
  }

  @Transactional(readOnly = true)
  public List<CalibrationRun> list(UUID courseId) {
    return runs.list(courseId);
  }
}
