package ar.edu.utn.frc.tup.piv.llm.adapter.out.messaging;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes outbound contracts to the transactional outbox; a separate dispatcher publishes them. */
@Component
public class KafkaEventPublisher {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  public KafkaEventPublisher(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

  public void publishDeferred(UUID attemptId, UUID challengeId, String courseCohortId,
      String traceparent, String requestId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("attemptId", attemptId); data.put("challengeId", challengeId); data.put("courseCohortId", courseCohortId);
    send("score_pendiente_diferido.v1", attemptId.toString(), data, traceparent, requestId);
  }

  private void send(String topic, String key, Map<String, Object> data, String traceparent, String requestId) {
    try {
      Map<String, Object> envelope = Map.of("eventId", UUID.randomUUID(), "version", "1.0",
          "occurredAt", OffsetDateTime.now(), "producer", "llm-service", "data", data);
      jdbc.update("""
          insert into llm.outbox_events(id, topic, event_key, payload, traceparent, request_id)
          values (?, ?, ?, cast(? as jsonb), ?, ?)
          """, UUID.randomUUID(), topic, key, json.writeValueAsString(envelope), traceparent, requestId);
    } catch (Exception error) {
      throw new IllegalStateException("No se pudo publicar el evento Kafka", error);
    }
  }
}
