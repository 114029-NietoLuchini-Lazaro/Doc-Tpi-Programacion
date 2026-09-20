# Contrato de integración llm-service ↔ Tema 05 (Desafíos Prácticos) — v1

> **Para quién:** el equipo de Tema 05. **Estado:** vigente y probable hoy contra el modo test (bot
> `fake`, sin modelo real). **Garantía:** este contrato **no cambia** cuando pasemos al modelo real.
> Este documento se entiende solo. Lo ejecutable, para validar contra un cliente o un consumidor,
> está en el [OpenAPI](../llm-service.openapi.yaml) (`/tutor/interactions`) y el
> [AsyncAPI](../llm-service.asyncapi.yaml) (`practice-events`, `evaluation-events`); si lo reciben
> como archivos sueltos, los dos acompañan a este.

## Por qué no cambia al pasar al servicio real

El bot y el modelo real se conectan al mismo endpoint, al mismo circuito de eventos y a los mismos
guardarraíles. Pasar de uno al otro es una asignación de modelo por función
(`PUT /api/llm/model-assignments/{function}`), sin redeploy ni cambios de código de ustedes.
Lo único que cambia es el **contenido** de lo que devolvemos, nunca su forma:

| Cambia con el modelo real | No cambia |
|---|---|
| El texto del tutor (hoy una pregunta socrática fija) | Rutas, verbos, headers, scope |
| Los puntajes (hoy 55-95 por hash del prompt) | Campos y tipos de request, response y eventos |
| `evaluator.provider` / `evaluator.model` (hoy `fake` / `fake-evaluator-v1`); tratarlos como texto opaco | Códigos HTTP y forma del error |
| La latencia y la posibilidad real de `state: unavailable` / `SCORE-DEFERRED` | Topics (una vez acordados), `eventType`, Message Key, `eventVersion` |

## 1. Tutor (HTTP)

`POST /api/llm/tutor/interactions` — siempre por el API Gateway.

**Identidad.** Servicio `practice-service`, scope M2M **`llm.tutor.interact`**, con usuario delegado. El Gateway
agrega `X-Service-Id`, `X-Service-Scopes` y `X-Delegated-User` a partir del JWT, y propaga
`traceparent` y `X-Request-Id`.

**Headers de ustedes:** `Idempotency-Key` (UUID, obligatorio) y `Content-Type: application/json`.

### Request

| Campo | Tipo | | Notas |
|---|---|---|---|
| `attemptId` | UUID | obligatorio | |
| `challengeId` | UUID | obligatorio | |
| `courseCohortId` | UUID | obligatorio | |
| `learnerId` | UUID | obligatorio | |
| `message` | string | obligatorio | No puede estar en blanco. Sin límite de largo validado hoy |
| `riskLevel` | `low` \| `medium` \| `high` | obligatorio | Lo fijan ustedes según el tipo de desafío. Con `low` no corre el guardarraíl de salida |
| `conversacionId` | UUID | opcional | Agrupa turnos. Si no viene se abre una conversación y se devuelve su id |
| `expectedSolution` | string | opcional | Solución esperada del desafío. **Solo la usa el guardarraíl de salida, en memoria**: no se guarda, loguea, audita ni devuelve |

Los campos desconocidos se ignoran.

```json
{
  "attemptId": "b1e2c3d4-0001-4a00-8000-000000000001",
  "challengeId": "b1e2c3d4-0002-4a00-8000-000000000002",
  "courseCohortId": "b1e2c3d4-0003-4a00-8000-000000000003",
  "learnerId": "b1e2c3d4-0004-4a00-8000-000000000004",
  "message": "No entiendo por qué mi recursión no corta en el caso base",
  "riskLevel": "medium",
  "conversacionId": "b1e2c3d4-0005-4a00-8000-000000000005",
  "expectedSolution": "return n <= 1 ? 1 : n * factorial(n - 1);"
}
```

### Response `200`

| Campo | Tipo | Notas |
|---|---|---|
| `message` | string | Siempre presente. En `completed`, el mensaje del tutor; en `unavailable`, un aviso fijo que pueden mostrar o reemplazar |
| `state` | `completed` \| `blocked` \| `unavailable` | `blocked` está **reservado** para cuando exista streaming: hoy no se produce, pero manéjenlo para no tener que tocar nada después |
| `conversacionId` | UUID | Siempre presente |

```json
{
  "message": "¿Qué pasa con `n` en cada llamada recursiva? Fijate qué valor tiene justo antes de que se cumpla la condición de corte.",
  "state": "completed",
  "conversacionId": "b1e2c3d4-0005-4a00-8000-000000000005"
}
```

### Errores

Cuerpo `application/problem+json` (RFC 7807).

| Código | Cuándo |
|---|---|
| `401` | Servicio distinto de `practice-service` o falta el scope `llm.tutor.interact` |
| `403` | Falta la identidad delegada o no es un UUID |
| `422` | Campo obligatorio ausente, `message` en blanco o `riskLevel` fuera del enum |

**Un fallo del modelo no es un error HTTP:** responde `200` con `state: unavailable`. No hay
cuota por alumno hoy. Cualquier otro `4xx`/`5xx` que aparezca (por ejemplo `429` si se agrega una
cuota) trátenlo como fallo.

### Comportamiento que pueden usar

- **Idempotencia.** Mismo `Idempotency-Key` y mismo cuerpo → misma respuesta, sin volver a invocar al
  modelo. Es seguro reintentar ante un timeout. El `expectedSolution` no entra en la comparación.
- **Guardarraíl de entrada.** Un intento de jailbreak devuelve un mensaje fijo con `state: completed`,
  sin llamar al modelo.
- **Guardarraíl de salida** (solo `riskLevel` `medium`/`high`). Si la respuesta trae un bloque de
  código de más de 8 líneas, o contiene literalmente el `expectedSolution` (sin distinguir
  mayúsculas), se reemplaza por una redirección socrática y el `state` sigue en `completed`. Sin
  `expectedSolution` solo actúa la regla del bloque de código.
- **Timeout.** Cada llamada al modelo espera hasta 8 s y se reintenta hasta 3 veces: con un proveedor
  real que no responde, la respuesta puede tardar unos 25 s antes de llegar como `unavailable`. Con el
  bot es inmediata. Recomendamos un timeout de cliente de 30 s.

## 2. Evaluador (Kafka)

Ustedes publican el cierre del intento y nosotros devolvemos el score. Envelope estándar de la
plataforma (`eventId`, `eventType`, `eventVersion`, `timestamp`, `producer`, `payload`).

> **Los nombres de topic son una propuesta nuestra.** `practice-events` y `evaluation-events` no
> figuran todavía en la tabla de dominios del [estándar de Kafka](../KAFKA_EVENT_STANDARD.md) (§17), que
> hoy lista `challenge-events` y otros. Hay que acordarlos con ustedes y registrarlos ahí. Si
> ustedes ya publican en otro topic, lo cambiamos en nuestra configuración sin tocar los campos.

### Entrada: `ATTEMPT-CLOSED` en `practice-events`

| Campo del `payload` | Tipo | |
|---|---|---|
| `attemptId` | UUID | obligatorio |
| `courseCohortId` | UUID | obligatorio |
| `learnerId` | UUID | obligatorio |
| `transcript` | array | obligatorio; la conversación alumno-tutor completa, sin truncar |

Forma recomendada de cada elemento del `transcript`: `{ "role": "student" | "tutor", "content": "..." }`.
Solo validamos que sea un array; el evaluador real lee el contenido tal cual, así que cuanto más
completo, mejor puntúa. Si algún día validáramos la forma, sería con un `eventVersion` nuevo.

`eventVersion` de partida: `1`. La Message Key la eligen ustedes; no dependemos de ella.

```json
{
  "eventId": "9f0c6c1e-6d0b-4c53-9a3e-0b1f6c8f2a11",
  "eventType": "ATTEMPT-CLOSED",
  "eventVersion": 1,
  "timestamp": "2026-09-19T15:00:00Z",
  "producer": "practice-service",
  "payload": {
    "attemptId": "b1e2c3d4-0001-4a00-8000-000000000001",
    "courseCohortId": "b1e2c3d4-0003-4a00-8000-000000000003",
    "learnerId": "b1e2c3d4-0004-4a00-8000-000000000004",
    "transcript": [
      { "role": "student", "content": "No entiendo por qué mi recursión no corta" },
      { "role": "tutor", "content": "¿Qué pasa con `n` en cada llamada?" }
    ]
  }
}
```

### Salida: `evaluation-events`, Message Key = `courseCohortId`

**`SCORE-CALCULATED`** (`eventVersion` 1):

| Campo | Tipo | Notas |
|---|---|---|
| `attemptId`, `courseCohortId`, `learnerId` | UUID | Los del evento de entrada |
| `rubricVersionId` | UUID | Rúbrica con la que se evaluó |
| `score` | entero 0-100 | Agregado ponderado con los pesos de la rúbrica, calculado por código (no por el modelo) |
| `dimensions` | objeto | `autonomy`, `clarity`, `progression`, `compliance`, `efficiency`: enteros 0-100 |
| `evaluator` | `{provider, model}` | Texto opaco |

```json
{
  "attemptId": "b1e2c3d4-0001-4a00-8000-000000000001",
  "courseCohortId": "b1e2c3d4-0003-4a00-8000-000000000003",
  "learnerId": "b1e2c3d4-0004-4a00-8000-000000000004",
  "rubricVersionId": "10000000-0000-0000-0000-000000000002",
  "score": 77,
  "dimensions": { "autonomy": 80, "clarity": 71, "progression": 68, "compliance": 90, "efficiency": 74 },
  "evaluator": { "provider": "fake", "model": "fake-evaluator-v1" }
}
```

**`SCORE-DEFERRED`** (`eventVersion` 1) cuando no se pudo evaluar: `attemptId`, `courseCohortId`,
`learnerId`, `reason` (`MODEL_UNAVAILABLE` \| `INVALID_MODEL_RESPONSE` \| `RUBRIC_UNAVAILABLE`) y
`retryFrom` (instante ISO-8601 sugerido para reintentar).

### Garantías

| Caso | Qué hacemos |
|---|---|
| Mismo `eventId` repetido | Se ignora; no se vuelve a publicar el score |
| Otro `eventType` en `practice-events` | Se registra y se ignora |
| Falta `attemptId`, `courseCohortId` o `learnerId`, no son UUID, o `transcript` no es un array | Va a `practice-events.dlt`; no se evalúa |
| Evaluación fallida | `SCORE-DEFERRED` con el `reason` |
| Reevaluar un intento | Publicar de nuevo `ATTEMPT-CLOSED` con un `eventId` nuevo; llega otro score. Hoy **no reintentamos solos** un diferido |

Consuman los eventos de score de forma idempotente por `eventId`. Nuestros mensajes llevan como
headers `eventId`, `eventType`, `eventVersion` y, si los hay, `traceparent` y `X-Request-Id`.
Nunca otorgamos XP: el score se lo reenvían ustedes a Tema 03.

## 3. Decisiones tomadas

Cada decisión se puede cambiar sin tocar el contrato salvo donde se indica.

| # | Decisión | Por qué |
|---|---|---|
| D1 | Un fallo del modelo es `200` + `state: unavailable`, no un `5xx` | El alumno tiene que ver algo en la pantalla del desafío; el error HTTP queda para fallas de integración |
| D2 | La solución esperada la mandan ustedes en `expectedSolution`, opcional | Ustedes controlan cuándo y cuánto exponen; no necesitamos un cliente ni credenciales hacia ustedes |
| D3 | El guardarraíl de salida compara por coincidencia literal y por bloques largos de código | Es lo implementado y probado. Una comparación por similitud (el umbral del 70%) mejora la detección sin cambiar el contrato |
| D4 | `blocked` queda reservado en el enum, hoy sin uso | Evita cambiar el contrato cuando se agregue el streaming |
| D5 | El score sale por Kafka a ustedes; nunca hablamos directo con Tema 03 | Decisión del 2026-09-13 |
| D6 | Message Key de `evaluation-events`: `courseCohortId` | Ordena los scores de una cohorte |
| D7 | Rúbrica única (la plantilla institucional) para todas las cohortes hasta que exista un mapa cohorte → curso | Es interno; lo único visible es `rubricVersionId` en la respuesta, que ya viaja |
| D8 | Campos, tipos, `eventType` y `eventVersion: 1` de los eventos como figuran en el AsyncAPI | Un cambio incompatible sería `eventVersion: 2`, con aviso previo |
| D9 | Los nombres de topic (`practice-events`, `evaluation-events`) son una propuesta a acordar | Cambiarlos es configuración nuestra y de ustedes; no altera los campos de los eventos |

## 4. Cómo va a evolucionar (sin romper)

- **Solo cambios aditivos** dentro de una versión: campos opcionales nuevos, valores nuevos en el
  contenido de un campo abierto, códigos de error nuevos. Ignoren los campos que no conozcan.
- **Un cambio incompatible** sale como `eventVersion` nuevo (eventos) o como endpoint nuevo (HTTP),
  con aviso previo, y la versión anterior sigue funcionando en paralelo.
- **Streaming (SSE)** irá en un endpoint aparte, `/tutor/interactions/stream`. El actual no cambia.
- **Evento de ediciones y tests del IDE** (alimenta la dimensión autonomía): será un `eventType`
  nuevo. No modifica `ATTEMPT-CLOSED` ni `SCORE-CALCULATED`.

## 5. Qué no valida el modo test

- La calidad del tutor y de los puntajes, la latencia real y el estado `unavailable`.
- La calificación por curso: hoy usamos siempre la rúbrica institucional.
- Que el evaluador tenga contexto del desafío: el evento no lo trae. Un campo opcional
  (por ejemplo `challengeContext`) se puede sumar al `payload` sin cambiar la versión.

## 6. Qué necesitamos que nos confirmen (ninguno cambia el contrato)

1. **Topics.** Que `practice-events` (entrada) y `evaluation-events` (salida) les sirven como nombre, o
   en qué topic publican hoy. Los registramos juntos en el estándar.
2. **Broker.** A qué Kafka y en qué ambiente se conectan; el del compose de `llm-service` es solo
   local. Nosotros y ustedes tenemos que usar el mismo.
3. **Payload de score.** Que `SCORE-CALCULATED` y `SCORE-DEFERRED` les sirven tal cual están.
4. **Transcript.** Que pueden armar `{role, content}` como se recomienda arriba.
5. **Message Key** de `practice-events`, si tienen una preferencia de orden.
6. **Solución esperada.** Que aceptan mandarla en `expectedSolution` (o avisan si prefieren otro camino;
   sería un cambio de contrato y hay que decidirlo antes de integrar).
7. **Evento del IDE.** Quién lo genera, ustedes o Tema 06.
8. **Pantalla de tutor no disponible.** Qué muestran cuando llega `state: unavailable`.

## 7. Cómo probar la conexión

Con el servicio levantado (`docker compose up`; Kafka viene activo):

- **Tutor.** `POST /api/llm/tutor/interactions` por el Gateway. Para pegarle directo desde el host,
  el overlay `compose.debug.yaml` publica el puerto 8086 y `compose.workbench.yaml` saltea la
  autenticación.
- **Evaluador.** Publicar el `ATTEMPT-CLOSED` de arriba en `practice-events` y leer `evaluation-events`.
  Dentro de la red de compose el broker es `kafka:9092`.
