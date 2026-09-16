package ar.edu.utn.frc.tup.piv.llm.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once outbox dispatch; consumers deduplicate with the envelope eventId. */
@Component
public class KafkaOutboxDispatcher {
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  public KafkaOutboxDispatcher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) { this.jdbc = jdbc; this.kafka = kafka; }

  @Scheduled(fixedDelayString = "${llm.kafka.outbox-delay-ms:1000}")
  @Transactional
  public void dispatch() {
    List<OutboxEvent> events = jdbc.query("""
        select id, topic, event_key, payload::text, traceparent, request_id
        from llm.outbox_events where published_at is null order by created_at
        for update skip locked limit 20
        """, (row, index) -> new OutboxEvent(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
        row.getString(4), row.getString(5), row.getString(6)));
    for (OutboxEvent event : events) {
      var headers = new RecordHeaders();
      if (event.traceparent() != null) headers.add("traceparent", event.traceparent().getBytes(StandardCharsets.UTF_8));
      if (event.requestId() != null) headers.add("X-Request-Id", event.requestId().getBytes(StandardCharsets.UTF_8));
      kafka.send(new ProducerRecord<>(event.topic(), null, event.key(), event.payload(), headers)).join();
      jdbc.update("update llm.outbox_events set published_at = now() where id = ?", event.id());
    }
  }
  private record OutboxEvent(UUID id, String topic, String key, String payload, String traceparent, String requestId) {}
}
