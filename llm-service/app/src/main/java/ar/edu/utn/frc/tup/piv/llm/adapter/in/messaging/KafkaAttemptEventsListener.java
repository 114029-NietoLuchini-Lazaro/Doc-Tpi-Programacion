package ar.edu.utn.frc.tup.piv.llm.adapter.in.messaging;

import ar.edu.utn.frc.tup.piv.llm.application.service.EvaluationAvailabilityService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.messaging.KafkaEventPublisher;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ChallengeCalibrationAssignmentRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes the external attempt-close contract. It contains no scoring fallback: an unavailable
 * evaluator becomes a real deferred evaluation, never a fabricated internal result.
 */
@Component
public class KafkaAttemptEventsListener {
  private static final String CONSUMER = "llm-service.attempt-closed";
  private final ObjectMapper json;
  private final ProcessedEventRepository processed;
  private final ChallengeCalibrationAssignmentRepository assignments;
  private final EvaluationAvailabilityService availability;
  private final KafkaEventPublisher events;

  public KafkaAttemptEventsListener(ObjectMapper json, ProcessedEventRepository processed,
      ChallengeCalibrationAssignmentRepository assignments, EvaluationAvailabilityService availability,
      KafkaEventPublisher events) {
    this.json = json; this.processed = processed; this.assignments = assignments;
    this.availability = availability; this.events = events;
  }

  @Transactional
  @KafkaListener(topics = "intento_cerrado.v1", groupId = "llm-service")
  public void onAttemptClosed(ConsumerRecord<String, String> record) {
    JsonNode envelope = parse(record.value());
    UUID eventId = requiredUuid(envelope, "eventId");
    if (!processed.claim(eventId, CONSUMER)) return;
    JsonNode data = envelope.required("data");
    UUID attemptId = requiredUuid(data, "attemptId");
    UUID challengeId = requiredUuid(data, "challengeId");
    assignments.lockOnFirstAttempt(challengeId, attemptId);
    if (availability.queueWhenUnavailable(attemptId, challengeId, eventId)) {
      events.publishDeferred(attemptId, challengeId, data.path("courseCohortId").asText(null),
          header(record, "traceparent"), header(record, "X-Request-Id"));
    }
  }

  private JsonNode parse(String value) {
    try { return json.readTree(value); }
    catch (Exception error) { throw new IllegalArgumentException("Evento intento_cerrado inválido", error); }
  }
  private UUID requiredUuid(JsonNode node, String field) {
    if (!node.hasNonNull(field)) throw new IllegalArgumentException("Evento sin " + field);
    try { return UUID.fromString(node.get(field).asText()); }
    catch (IllegalArgumentException error) { throw new IllegalArgumentException("UUID inválido en " + field, error); }
  }
  private String header(ConsumerRecord<String, String> record, String name) {
    var header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
