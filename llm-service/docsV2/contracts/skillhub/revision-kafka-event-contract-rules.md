# Revisión preparada: `kafka-event-contract-rules` (v4) — NO enviada

**Estado (2026-09-20):** **PREPARADA, no enviada.** La entrada publicada (v3, `owning_team: LLM`) describe un estándar que
ya no rige: topic por evento `<event>.v<major>` y envelope `eventId`, `version`, `occurredAt`, `producer`, `data`. El
estándar vigente es el del PDF `KAFKA.pdf` (ver [`../KAFKA_EVENT_STANDARD.md`](../KAFKA_EVENT_STANDARD.md), ADR-020). Se
envía con `propose_revision` solo cuando se pida. Después de aceptada, hay que **resincronizar el espejo local**
`.skill-hub/kafka-event-contract-rules.md` con `get_skill` (no editarlo a mano) y el skill local
`.agents/skills/contratos-kafka/`, que ya está en el estándar nuevo.

**`description`:** Fixed rules an async event contract must meet to travel the platform bus: five-field envelope without eventVersion, UPPER_SNAKE_CASE event types, topics assigned by the notifications group, at-least-once idempotent consumers, outbox publishing.

**`when_to_use`:** defining, publishing or consuming a Kafka event; writing the AsyncAPI; naming an event type; requesting a topic; envelope fields; idempotent consumer; outbox pattern; Spring Kafka configuration

**`tags`:** asyncapi, contracts, events, idempotency, kafka, outbox

**`rationale`:** The course staff's Kafka standard (KAFKA.pdf) replaced the rules this entry described. The envelope has five fields and no `eventVersion`, event types are `UPPER_SNAKE_CASE`, groups cannot create topics and the bus is `event-bus:29092`. Idempotent consumers, the outbox and the correlation headers are not in the PDF and do not contradict it, so they are kept and marked as house rules. Versioning without `eventVersion` is undefined in the PDF and is left as an open point instead of inventing a rule.

**`content`:**

````markdown
## Rule

This is a **reference to consult before defining, publishing or consuming an event, or writing the AsyncAPI**, not a generator. The source is the platform Kafka standard (KAFKA.pdf); the notifications group owns the topics. Correlation is covered separately by [[request-correlation-across-http-and-kafka]].

1. **Envelope: exactly five fields**, all required, everything emitted in English: `eventId` (uuid, doubles as the idempotency key), `eventType`, `timestamp` (ISO-8601 UTC), `producer` (emitting service), `payload` (object). **No `eventVersion`.** Do not rename, remove or add envelope fields.
2. **`eventType` in `UPPER_SNAKE_CASE`**, a fact that already happened, not a command: `CHALLENGE_COMPLETED`, `SCORE_CALCULATED`. Stable once published.
3. **Topics are assigned, not created.** Groups cannot create topics: ask the notifications group. Until they assign one, mark it provisional (`x-topic-status: pending-assignment` in the AsyncAPI). There is no dead-letter topic; keep rejected messages in the service's own storage.
4. **Connection.** Spring for Apache Kafka; `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP:event-bus:29092}`; consumer `group-id` = the service name. A publisher that sends plain JSON text (for example from an outbox) sends no `__TypeId__` header: consumers using `JsonDeserializer` set `spring.json.use.type.headers=false` and a default type. Spring's `JsonSerializer` writes an `Instant` as a number, not the ISO-8601 text of the standard's example: use `@JsonFormat(shape = STRING)`.
5. **One event per business fact**, not one per consumer. The payload describes the fact; it is not a container for every field some consumer might want. A topic that mixes event types with different payloads is consumed as `Event<?>` and branched on `eventType`.
6. **At-least-once, idempotent consumers** (house rule) — the bus may redeliver. Every consumer dedups on `eventId` (or `(consumer, eventId)`) so processing twice has no double effect. No total order across topics; if order matters, same topic + same message key.
7. **Publish via outbox** (house rule) — write the event to an outbox table in the *same transaction* as the state change; a relay publishes it. Never call `producer.send()` by hand inside business logic.
8. **Versioning: open point.** The envelope has no `eventVersion` and the standard does not say how to evolve an event. Until the notifications group defines it: additive changes only (consumers ignore unknown fields); an incompatible change is announced in writing and coordinated with the consumers, never done unilaterally.
9. **Cross-team fields** — a field another team needs is agreed before the contract freezes, documented with owner and date. Don't add fields "just in case"; don't modify another team's event contract.

Publishing an event is not a POST to another microservice: if you need a response it is HTTP, if you notify a fact that already happened it is an event. The notifications group owns the topics and the bus; the payload of *your* events (which fields, which enums) is yours.
````
