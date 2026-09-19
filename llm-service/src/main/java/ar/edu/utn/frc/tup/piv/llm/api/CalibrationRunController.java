package ar.edu.utn.frc.tup.piv.llm.api;

import ar.edu.utn.frc.tup.piv.llm.application.CalibrationRunService;
import ar.edu.utn.frc.tup.piv.llm.domain.calibration.CalibrationRun;
import ar.edu.utn.frc.tup.piv.llm.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.private-path}/courses/{courseId}/calibrations")
public class CalibrationRunController {
  private final CalibrationRunService s;
  private final GoldenSetAuthorization a;
  private final CourseAuthorization c;

  @Autowired
  public CalibrationRunController(CalibrationRunService s, GoldenSetAuthorization a, CourseAuthorization c) {
    this.s = s;
    this.a = a;
    this.c = c;
  }

  @GetMapping
  public CalibrationPage list(@PathVariable UUID courseId, @RequestHeader HttpHeaders h) {
    auth(courseId, h);
    return new CalibrationPage(s.list(courseId));
  }

  @PostMapping
  public ResponseEntity<CalibrationRun> create(@PathVariable UUID courseId, @RequestBody Request r,
      @RequestHeader("Idempotency-Key") UUID key, @RequestHeader HttpHeaders h) {
    var x = auth(courseId, h);
    return ResponseEntity.accepted().body(s.enqueue(courseId, r.rubricVersionId(), r.goldenSetVersionId(), r.modelDeploymentId(), key, x));
  }

  @GetMapping("/{runId}")
  public CalibrationRun get(@PathVariable UUID courseId, @PathVariable UUID runId, @RequestHeader HttpHeaders h) {
    auth(courseId, h);
    return s.get(courseId, runId);
  }

  private CallerIdentity auth(UUID id, HttpHeaders h) {
    var x = a.require(h);
    c.requireTeacher(id, x, h);
    return x;
  }

  public record Request(UUID rubricVersionId, UUID goldenSetVersionId, UUID modelDeploymentId) {}
  public record CalibrationPage(List<CalibrationRun> items) {}
}
