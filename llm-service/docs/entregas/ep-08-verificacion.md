# EP-08 · Moderación integrada — verificación (2026-09-18)

Verificado contra el servicio real (Docker: `pgvector/pg16` + `llm-service`, overlay `compose.debug.yaml`, Flyway hasta V28), no solo con tests.

## Defecto encontrado y corregido

| | |
|---|---|
| **Síntoma** | `POST /decisions` devolvía `incident_id` en cada BLOCK, pero `moderation_incidents` quedaba vacía: apelar daba 404 y el listado docente salía vacío. |
| **Causa** | Nada en producción creaba el incidente (`ModerationIncident.ofBlock` solo se usaba en tests, que sembraban datos a mano y por eso pasaban). Además el pedido de chat no traía el id del alumno, así que no había dueño para el incidente. |
| **Corrección** | `sender_id` obligatorio en el pedido (contrato v1.1.0, breaking) y `ModerationDecisionService` abre el incidente en todo resultado ≠ ALLOW: `BLOCK` → estado `BLOCK` (apelable); `PENDING`/`PENDING_REVIEW` → `PENDING_REVIEW` (visible al docente, no apelable). Preview recortado a 200 caracteres. Un reintento idempotente no duplica el incidente. |
| **Tests nuevos** | `ModerationDecisionServiceTest`: incidente del BLOCK con dueño y preview recortado; ALLOW no abre incidente; reintento idempotente no duplica. |

## Resultados en vivo

| Caso | Resultado |
|---|---|
| Mensaje limpio | 200 `ALLOW/CLEAN`, sin texto persistido |
| Mismo `message_id` (mismo texto y texto distinto) | 200, misma decisión original |
| ≥ 3 URLs / Base64 de código / insultos | 200 `BLOCK` con `SPAM` / `CODE_OBFUSCATION` / `OFFENSIVE` |
| Texto educativo sobre Base64 | 200 `ALLOW` |
| Texto vacío / sin `sender_id` | 400 |
| Sin headers / scope incorrecto | 401 |
| Clasificador contextual caído | 200 `PENDING_REVIEW`, `degradation_reason: CONTEXTUAL_UNAVAILABLE`, nunca ALLOW |
| Apelar incidente ajeno / propio / 2ª vez | 403 / 201 `PENDING_REVIEW` / 409 `appeal_already_exists` |
| Listado docente (curso propio / ajeno) | 200 con el incidente y `has_appeal: true` / 403 |
| Resolver (motivo corto / válido) | 400 / 200 `REVERSED`, `resolved_by` = docente |
| Detalle tras resolver | 200 `status: REVERSED` |

Tests: suite completa `mvn test` BUILD SUCCESS; moderación 177 tests (unitarios + 3 ITs) en verde.

## Pendiente / no verificado
- Con el clasificador contextual, sin API key de OpenAI, solo se ejercitó el camino degradado; el camino `contextual` exitoso no se probó en vivo.
- `CA_negativo_1` de LLM-S11-H01 (retirar un ALLOW sin revisión = error de protocolo) sigue sin implementar.
- Retención/purga (H06) y `PUT` de política: cubiertas por tests, no ejercitadas en vivo.
- Las fichas `docs/historias/ep-08/*` y el espejo `docsV2/` no se actualizaron (checkboxes ni contrato).
- `chat-service` debe empezar a enviar `sender_id` (breaking): coordinarlo antes de integrar.

## Para los grupos
- **Mock (sin dependencias):** `docs/contracts/MOCK.md` §5 — `npx @stoplight/prism-cli mock docs/contracts/llm-service-v1-moderacion.openapi.yaml --port 4011`.
- **Servicio real:** `docs/sprints/sprint-4/runbook-verificacion-moderacion.md` (headers `X-*`, no JWT).
- **Contrato:** `docs/contracts/llm-service-v1-moderacion.openapi.yaml` v1.1.0 + adenda actualizada; el borrador viejo quedó marcado OBSOLETO.
