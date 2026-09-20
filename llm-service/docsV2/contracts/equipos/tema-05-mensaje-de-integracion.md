# Mensaje para Tema 05 — integración con `llm-service` (2026-09-20)

> Texto listo para pegar en el canal de Tema 05 (Desafíos Prácticos). La fuente de verdad sigue siendo la guía
> [`llm-service-contrato-para-desafios-practicos.md`](llm-service-contrato-para-desafios-practicos.md) (v2) y, para su
> asistente de IA, el skill [`tema-05-skill-integrar-llm-service/SKILL.md`](tema-05-skill-integrar-llm-service/SKILL.md).
> Si el Skill Hub todavía no aceptó las revisiones ([`../skillhub/README.md`](../skillhub/README.md)), hay que pasarles
> esos dos archivos: la guía manda sobre lo que diga el hub.

---

Hola equipo de Desafíos Prácticos 👋 Ya pueden integrar `practice-service` con `llm-service`. Con esto se conectan al **tutor de IA** y al **evaluador**. Hoy responde un **bot de prueba** (sin modelo real): sirve para probar la conexión, los errores y los eventos. Cuando pasemos al modelo real **no tienen que cambiar código**; solo cambia el contenido de las respuestas.

**Qué necesitan (2 archivos, en español):**
1. **Guía `llm-service-contrato-para-desafios-practicos.md` (v2).** Es el único documento que hace falta: trae el paso a paso, el contrato completo y los dos contratos ejecutables (OpenAPI del tutor y AsyncAPI de los eventos).
2. **Skill `integrar-llm-service`** (`SKILL.md`), para que su asistente de IA arme el cliente del tutor, el publicador de `ATTEMPT_CLOSED` y el consumidor de scores. Tiene que estar en la misma carpeta que la guía.

**Cómo se conectan:**
- **Tutor (HTTP):** `POST /api/llm/tutor/interactions` por el **API Gateway**, con un token `client_credentials` (`audience: llm-service`, scope `llm.tutor.interact`) y una `Idempotency-Key` nueva por mensaje. Responde siempre `200`; si el modelo no puede, viene `state: unavailable`.
- **Evaluador (Kafka):** cuando cierran un intento, publican **`ATTEMPT_CLOSED`** con la conversación completa; nosotros les devolvemos **`SCORE_CALCULATED`** (o **`SCORE_DEFERRED`**) con un puntaje de 0 a 100 y su desglose. Nunca otorgamos XP: el score llega a ustedes y ustedes se lo reenvían a Tema 03.

**Los eventos siguen el estándar Kafka de la cátedra (`KAFKA.pdf`)**, así que ojo con esto:
- El envelope tiene **5 campos**: `eventId`, `eventType`, `timestamp`, `producer`, `payload`. **No hay `eventVersion`.** Todo en inglés.
- Los `eventType` van en `MAYÚSCULAS_CON_GUION_BAJO`: `ATTEMPT_CLOSED`, `SCORE_CALCULATED`, `SCORE_DEFERRED`.
- El bus es **`event-bus:29092`** (variable `KAFKA_BOOTSTRAP`); el `group-id` es el nombre de su servicio (por ejemplo `practice-service`).
- **Los tópicos no se crean**: los asigna el grupo de Notificaciones. Hoy usamos `practice-events` (ustedes publican) y `evaluation-events` (ustedes leen) como nombres **provisorios**: déjenlos como propiedad configurable.
- No hay tópico de dead-letter: los eventos inválidos los guardamos nosotros en una tabla y no generan score.

**Tres cosas que suelen romper la integración:**
1. `evaluation-events` mezcla **dos tipos de payload**, así que un consumidor tipado por un solo payload no alcanza: consuman `Event<?>` (o el JSON) y **ramifiquen por `eventType`**.
2. Nosotros mandamos JSON como texto y **sin el header `__TypeId__`**. Si usan `JsonDeserializer` de Spring, pongan `spring.json.use.type.headers: false` y un tipo por defecto (`spring.json.value.default.type`).
3. El `JsonSerializer` de Spring escribe un `Instant` como **número**, no como el `"2026-09-20T15:00:00Z"` del ejemplo del PDF. Para publicar texto ISO-8601: `@JsonFormat(shape = STRING)` en el campo. Nosotros publicamos texto y no leemos el `timestamp` de su evento.

**Para probar:** con el bot, un `ATTEMPT_CLOSED` válido devuelve un `SCORE_CALCULATED` en unos segundos; repetir el mismo `eventId` no duplica el score. El bot **no** produce `unavailable` ni `SCORE_DEFERRED`: pídannos que forcemos el fallo para probar esas pantallas. En local pueden usar el compose de `llm-service` (broker `kafka-local:29092`); el detalle está en la sección 11 de la guía.

**Qué necesitamos que nos confirmen** (sección 10 de la guía; ninguno cambia el contrato): (1) si `practice-events` y `evaluation-events` les sirven como nombres mientras Notificaciones asigna los definitivos; (2) qué `producer` van a usar y si su `timestamp` sale como texto o número; (3) la Message Key de `practice-events`; (4) que pueden armar el `transcript` como `{role, content}`; (5) si aceptan mandar la solución esperada en `expectedSolution`; (6) quién genera el evento de ediciones del IDE (ustedes o Tema 06); (7) qué muestran cuando llega `state: unavailable`.

**Todavía no está verificado** (lo decimos para que no les sorprenda): el ruteo real por el API Gateway (si agrega la identidad delegada con un token de servicio), el bus `event-bus:29092` con tópicos definitivos, y la calidad del evaluador con el modelo real. Si les llega un `403` "Identidad delegada ausente" con un token correcto, es lo primero y no un error de ustedes.

Cualquier duda, en este canal. ¡Gracias!
