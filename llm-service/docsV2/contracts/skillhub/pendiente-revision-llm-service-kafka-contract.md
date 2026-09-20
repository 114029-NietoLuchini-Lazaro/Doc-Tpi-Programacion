# Revisión preparada: `llm-service-kafka-contract` (v5) — NO enviada

**Estado (2026-09-20):** **PREPARADA, no enviada.** El contrato publicado en el hub (v4) quedó desactualizado: usa el
envelope con `eventVersion`, `eventType` con guiones (`ATTEMPT-CLOSED`) y `practice-events.dlt`. El estándar vigente es
el del PDF `KAFKA.pdf` ([`../KAFKA_EVENT_STANDARD.md`](../KAFKA_EVENT_STANDARD.md), ADR-020). Adjunto a subir:
[`../llm-service.asyncapi.yaml`](../llm-service.asyncapi.yaml) (**v3.0.0**). El hub no se toca hasta que se pida.

**`description`:** Canonical AsyncAPI contract for the Kafka events llm-service publishes and consumes, including the attempt evaluation flow with practice-service. Follows the platform Kafka standard (five-field envelope, no eventVersion).

**`when_to_use`:** Use when consuming llm-service scores, calibration outcomes or security incident events, or publishing ATTEMPT_CLOSED, over Kafka: practice-events, evaluation-events, consumer group, dead letter.

**`tags`:** asyncapi, events, kafka, llm-service, microservices, practice-service, evaluator, contracts

**`rationale`:** The platform Kafka standard (KAFKA.pdf, from the course staff) replaced the envelope this contract used. The envelope now has exactly five fields (`eventId`, `eventType`, `timestamp`, `producer`, `payload`) and no `eventVersion`; event types are `UPPER_SNAKE_CASE` (`ATTEMPT_CLOSED`, `SCORE_CALCULATED`, `SCORE_DEFERRED`, `MESSAGE_UNBLOCKED`); the bus is `event-bus:29092`; groups cannot create topics, so there is no `.dlt` topic and the topic names are provisional until the notifications group assigns them. The attached AsyncAPI is v3.0.0 of the repo file. Verified against an embedded Kafka broker (envelope with exactly five fields, no `eventVersion` header, malformed event kept in a dead-letter table without blocking the partition).

**`content`:**

````markdown
## Rule

Use the attached AsyncAPI document as the canonical Kafka contract for events published and consumed by llm-service. Preserve the five-field envelope, the correlation headers, at-least-once delivery and eventId deduplication rules.

## Attempt evaluation (practice-service)

Publish a closed attempt as one event and read the score from another topic: nobody waits for the evaluation in the same call. llm-service never grants XP, so forward the score to the challenges engine yourself.

- **Bus and topics.** Bus `event-bus:29092` (env var `KAFKA_BOOTSTRAP`). Topic names are **provisional** (groups cannot create topics; the notifications group assigns them): `practice-events` (practice-service publishes, llm-service reads with group `llm-service`) and `evaluation-events` (llm-service publishes with key `courseCohortId`, practice-service reads with its **own** group, its service name). There is no dead-letter topic.
- **Envelope in the body**, as JSON text, exactly five fields: `eventId`, `eventType`, `timestamp`, `producer`, `payload`. No `eventVersion`. `eventId` and `eventType` are repeated as headers so consumers can filter without parsing.
- **`ATTEMPT_CLOSED` payload.** `attemptId`, `courseCohortId`, `learnerId`, `transcript` (the whole conversation as an array of `{role, content}`). One event per closed attempt, published through an outbox; reuse the same `eventId` when a send is retried.
- **`SCORE_CALCULATED` payload.** `attemptId`, `courseCohortId`, `learnerId`, `rubricVersionId`, `score` (0-100, weighted by code, not by the model), `dimensions` (`autonomy`, `clarity`, `progression`, `compliance`, `efficiency`), `evaluator` (`provider`, `model`, opaque text).
- **`SCORE_DEFERRED` payload.** `attemptId`, `courseCohortId`, `learnerId`, `reason` (`MODEL_UNAVAILABLE`, `INVALID_MODEL_RESPONSE`, `RUBRIC_UNAVAILABLE`), `retryFrom`. Deferred scores are not retried automatically: resend `ATTEMPT_CLOSED` with a new `eventId`.
- **Consumers.** `evaluation-events` mixes two event types with different payloads, so consume `Event<?>` (or the body as JSON) and branch on `eventType`; dedupe by `eventId`; keep the latest event per `attemptId`. Order is guaranteed only inside one `courseCohortId`. llm-service publishes plain JSON text with no `__TypeId__` header: with Spring's `JsonDeserializer` set `spring.json.use.type.headers=false` and a default type.
- **`timestamp`.** llm-service publishes ISO-8601 text in UTC. Spring's `JsonSerializer` writes an `Instant` as a number, so publish the text form (`@JsonFormat(shape = STRING)`); llm-service does not read the `timestamp` of `ATTEMPT_CLOSED`.
- **Bad input.** A repeated `eventId` is ignored. Missing or non-UUID ids, or a `transcript` that is not an array, are stored in llm-service's `event_dead_letter` table and produce no score.
- **Evolution.** Additive changes only. The standard has no `eventVersion` yet, so an incompatible change is announced in writing and coordinated with consumers (open point with the notifications group).

## Reasoning

This follows the platform Kafka standard (KAFKA.pdf, transcribed in `llm-service/docsV2/contracts/KAFKA_EVENT_STANDARD.md`). The older `kafka-event-contract-rules` entry described per-event topic names and a `data` envelope: see the revision prepared for it. Topic names, the `producer` value, the `timestamp` form and event versioning are still open with the notifications group; the flow was verified only against a local broker with the test bot. Full guide for the practice-service team: `llm-service/docsV2/contracts/equipos/llm-service-contrato-para-desafios-practicos.md` (v2).
````

---

# Texto anterior (v4, ya publicada — desactualizada)

## Revisión anterior de `llm-service-kafka-contract`

**Estado:** ENVIADA el 2026-09-20 (versión 2 pendiente de aceptación por un admin). Este archivo conserva el texto
tal como se envió, con el adjunto `../llm-service.asyncapi.yaml`.

**Por qué:** el YAML adjunto en el hub (8.543 bytes) es una copia exacta del repo en el commit `aff07ba2`, donde
`SCORE-CALCULATED` y `SCORE-DEFERRED` solo tienen un `Envelope` genérico: un consumidor no podría saber los
campos del score.

**`description`:** Canonical AsyncAPI contract for the Kafka events llm-service publishes and consumes, including the attempt evaluation flow with practice-service.

**`when_to_use`:** Use when consuming llm-service scores, calibration outcomes or security incident events, or publishing ATTEMPT-CLOSED, over Kafka: practice-events, evaluation-events, consumer group, dead letter.

**`tags`:** asyncapi, events, kafka, llm-service, microservices, practice-service, evaluator, contracts

**`rationale`:** The attached AsyncAPI was an older snapshot (repo commit aff07ba2) where SCORE-CALCULATED and SCORE-DEFERRED only had a generic Envelope payload, so a consumer could not know the score fields. Replaced it with the current repo file, which defines both payloads, and added the rules practice-service needs. Verified end to end against a local Kafka broker with the test bot (score, duplicate event, dead letter, deferred score); there is no shared broker yet, so topic names are still proposals.

**`content`:**

````markdown
## Rule

Use the attached AsyncAPI document as the canonical Kafka contract for events published and consumed by llm-service. Preserve the common envelope, correlation headers, at-least-once delivery and eventId deduplication rules.

## Attempt evaluation (practice-service)

Publish a closed attempt as one event and read the score from another topic: nobody waits for the evaluation in the same call. llm-service never grants XP, so forward the score to the challenges engine yourself.

- **Topics** (proposed names, still to be registered in the platform standard). `practice-events`: practice-service publishes, llm-service reads with group `llm-service`. `evaluation-events`: llm-service publishes with key `courseCohortId`, practice-service reads with its **own** group. `practice-events.dlt`: rejected events.
- **Envelope in the body**, as JSON text: `eventId`, `eventType`, `eventVersion`, `timestamp`, `producer`, `payload`. `eventId`, `eventType` and `eventVersion` are repeated as headers so consumers can filter without parsing.
- **`ATTEMPT-CLOSED` payload.** `attemptId`, `courseCohortId`, `learnerId`, `transcript` (the whole conversation as an array of `{role, content}`). One event per closed attempt, published through an outbox; reuse the same `eventId` when a send is retried.
- **`SCORE-CALCULATED` payload.** `attemptId`, `courseCohortId`, `learnerId`, `rubricVersionId`, `score` (0-100, weighted by code, not by the model), `dimensions` (`autonomy`, `clarity`, `progression`, `compliance`, `efficiency`), `evaluator` (`provider`, `model`, opaque text).
- **`SCORE-DEFERRED` payload.** `attemptId`, `courseCohortId`, `learnerId`, `reason` (`MODEL_UNAVAILABLE`, `INVALID_MODEL_RESPONSE`, `RUBRIC_UNAVAILABLE`), `retryFrom`. Deferred scores are not retried automatically: resend `ATTEMPT-CLOSED` with a new `eventId`.
- **Consumers.** Filter by `eventType`, dedupe by `eventId`, keep the latest event per `attemptId`. Order is guaranteed only inside one `courseCohortId`.
- **Bad input.** A repeated `eventId` is ignored. Missing or non-UUID ids, or a `transcript` that is not an array, go to `practice-events.dlt` and produce no score.
- **Evolution.** Additive changes only; an incompatible change is a new `eventVersion`.

## Reasoning

This follows the platform standard `KAFKA_EVENT_STANDARD.md`: one topic per domain and an envelope with `eventType` and `payload`; the older `kafka-event-contract-rules` entry still describes per-event topic names and a `data` envelope. The topic names and a shared broker are still open points, and the flow was verified only against a local broker with the test bot. Full guide for the practice-service team: `llm-service/docsV2/contracts/equipos/llm-service-contrato-para-desafios-practicos.md`.
````
