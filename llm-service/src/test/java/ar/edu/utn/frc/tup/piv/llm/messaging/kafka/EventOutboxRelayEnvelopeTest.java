package ar.edu.utn.frc.tup.piv.llm.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventOutboxRelayEnvelopeTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final UUID eventId = UUID.randomUUID();

  @Test
  void theBodyIsTheFullEnvelopeOfThePlatformStandard() throws Exception {
    var row = row("{\"attemptId\":\"a-1\",\"score\":77}");

    var body = mapper.readTree(EventOutboxRelay.envelopeJson(row, mapper));

    assertThat(body.fieldNames()).toIterable()
        .containsExactlyInAnyOrder("eventId", "eventType", "eventVersion", "timestamp", "producer", "payload");
    assertThat(body.get("eventId").asText()).isEqualTo(eventId.toString());
    assertThat(body.get("eventType").asText()).isEqualTo("SCORE-CALCULATED");
    assertThat(body.get("eventVersion").isInt()).isTrue();
    assertThat(body.get("eventVersion").asInt()).isEqualTo(1);
    assertThat(body.get("timestamp").asText()).isEqualTo("2026-09-19T15:00:00Z");
    assertThat(body.get("producer").asText()).isEqualTo("llm-service");
  }

  @Test
  void thePayloadIsEmbeddedAsAnObjectNotAsAnEscapedString() throws Exception {
    var body = mapper.readTree(EventOutboxRelay.envelopeJson(row("{\"attemptId\":\"a-1\",\"score\":77}"), mapper));

    assertThat(body.get("payload").isObject()).isTrue();
    assertThat(body.get("payload").get("score").asInt()).isEqualTo(77);
  }

  @Test
  void aPayloadThatIsNotJsonFailsLoudlyInsteadOfPublishingGarbage() {
    assertThatThrownBy(() -> EventOutboxRelay.envelopeJson(row("no es json"), mapper))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining(eventId.toString());
  }

  private EventOutboxRepository.OutboxRow row(String payloadJson) {
    return new EventOutboxRepository.OutboxRow(eventId, "evaluation-events", "cohorte-1", "SCORE-CALCULATED", 1,
        "llm-service", OffsetDateTime.of(2026, 9, 19, 15, 0, 0, 0, ZoneOffset.UTC), payloadJson, null, null, 0);
  }
}
