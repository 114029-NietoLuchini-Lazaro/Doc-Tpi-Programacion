# Adenda — Decisiones de Moderación de Chat (EP-08 · LLM-S11-H01)

Esta adenda formaliza el contrato HTTP síncrono acordado entre `chat-service` y `llm-service` para la moderación previa a la entrega de mensajes (RF-CHT-09).

> **Estado:** Implementado y verificado en `llm-service` (2026-09-18) · Contrato completo (decisiones, apelaciones, incidentes, resolución, retención) en [`llm-service-v1-moderacion.openapi.yaml`](../llm-service-v1-moderacion.openapi.yaml) v1.1.0 · Pendiente de fusión formal en `llm-service-v1.openapi.yaml` tras la validación conjunta con `chat-service`.
>
> **Cambio v1.1.0 (breaking): `sender_id` pasa a ser obligatorio** en `POST /moderation/v1/decisions`. Sin él el incidente no podía registrar a su dueño y las apelaciones y la revisión docente no funcionaban de punta a punta.

---

## 1. Identificación del Canal y Seguridad M2M

- **Protocolo:** HTTP/1.1 sincrónico (camino crítico del chat en vivo, no va por cola).
- **Rutas soportadas:**
  - `POST /moderation/v1/decisions`
  - `POST /api/llm/moderation/v1/decisions` (a través del API Gateway de plataforma)
- **Autenticación:** el servicio **no valida el JWT**; lo valida el API Gateway, que inyecta headers de identidad (DEC-08). El principal debe ser de tipo servicio con scope:
  - `scope`: `moderation:decide`
- **Cabeceras obligatorias:**
  - Vía Gateway: el JWT de servicio (`audience: llm-service`, scope `moderation:decide`) → el Gateway inyecta `X-Principal-Type: service`, `X-Service-Id` y `X-Service-Scopes: moderation:decide`.
  - Directo al servicio (dev/pruebas): enviar esas tres cabeceras `X-*`. Un `Authorization: Bearer` sin firma **no** autentica (solo con `app.security.trust-unsigned-bearer=true`, apagado por defecto).
  - `Content-Type: application/json`
- **Timeout máximo acordado:** 800 ms (si expira, el servicio responde `PENDING` de forma segura).

---

## 2. Esquemas de Datos

### Request Body (`ModerationDecisionRequest`)

```json
{
  "message_id": "msg-001",
  "course_id": "curso-42",
  "sender_id": "alumno-1",
  "sender_role": "student",
  "text": "Texto a moderar (máximo 4096 caracteres)",
  "context_flags": {
    "thread_id": "thread-99",
    "is_reply": true
  }
}
```

| Campo | Tipo | Requerido | Descripción / Validación |
|---|---|---|---|
| `message_id` | `string` | **Sí** | Identificador único del mensaje de chat. Clave de idempotencia. |
| `course_id` | `string` | **Sí** | Identificador del curso o cohorte. |
| `sender_id` | `string` | **Sí** (v1.1.0) | Id del usuario que escribió el mensaje (≤ 255). Es el dueño del incidente: solo él puede apelarlo. |
| `sender_role` | `string` | **Sí** | Rol del remitente (`student`, `teacher`, `system`). |
| `text` | `string` | **Sí** | Contenido textual a evaluar (1 a 4096 caracteres). |
| `context_flags` | `object` | No | Metadatos contextuales mínimos sin historial de conversación. |

---

### Response 200 OK (`ModerationDecisionResponse`)

```json
{
  "message_id": "msg-001",
  "decision": "ALLOW",
  "reason_code": "CLEAN",
  "classifier_used": "deterministic",
  "latency_ms": 15,
  "incident_id": null
}
```

| Campo | Tipo | Valores posibles | Descripción |
|---|---|---|---|
| `message_id` | `string` | — | Eco del identificador evaluado. |
| `decision` | `string` | `ALLOW`, `BLOCK`, `PENDING` | Veredicto de entrega del mensaje. Si es `BLOCK` o `PENDING`, el mensaje **no debe publicarse**. |
| `reason_code` | `string` | `CLEAN`, `SPAM`, `OFFENSIVE`, `CODE_OBFUSCATION`, `CONTEXTUAL_BLOCK`, `ENGINE_UNAVAILABLE`, `TIMEOUT` | Código tipado del motivo de la decisión. |
| `classifier_used` | `string` | `deterministic`, `contextual`, `fallback` | Mecanismo que resolvió la decisión. |
| `latency_ms` | `integer` | $\ge 0$ | Tiempo de procesamiento interno en milisegundos. |
| `incident_id` | `uuid \| null` | UUID o `null` | Identificador de auditoría. Obligatoriamente `null` si `ALLOW`; UUID asignado si `BLOCK` o `PENDING`. |

---

## 3. Respuestas de Error (Problem Details RFC 7807)

- **401 Unauthorized:**
  Emitido si no se envía token técnico o si carece del scope `moderation:decide`.
  ```json
  {
    "type": "about:blank",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Missing scope: moderation:decide"
  }
  ```
- **400 Bad Request:**
  Emitido si faltan campos obligatorios (`message_id`, `text`, `course_id`, `sender_role`) o si el texto excede los 4096 caracteres.
  ```json
  {
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Los datos enviados no son válidos o contienen campos obligatorios ausentes."
  }
  ```

---

## 4. Garantías de Diseño

1. **Idempotencia:** Enviar múltiples veces el mismo `message_id` retorna la misma decisión original sin duplicar registros de auditoría. Si el texto entrante difiere del previamente registrado para el mismo ID, se devuelve la decisión previa y se emite alerta en log.
2. **Minimización de Datos:** Si la decisión es `ALLOW`, la auditoría inmutable en base de datos NUNCA persiste el texto del mensaje (`message_text` queda en `NULL`).
3. **Fallback Seguro (Fail-Closed):** Ante demoras > 800 ms o indisponibilidad del motor de clasificación, la respuesta es siempre `PENDING` (nunca `ALLOW` por defecto).
4. **Incidentes:** toda decisión distinta de `ALLOW` abre un incidente cuyo id se devuelve en `incident_id`: `BLOCK` → incidente `BLOCK` (apelable por `sender_id`); `PENDING` / `PENDING_REVIEW` → incidente `PENDING_REVIEW` (visible al docente del curso, no apelable). El chat **no** publica ninguno de los tres.
