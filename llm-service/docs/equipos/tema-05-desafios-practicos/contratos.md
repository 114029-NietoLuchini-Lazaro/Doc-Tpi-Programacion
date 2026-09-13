# Tema 05 — Desafíos Prácticos — contratos

> Fuente completa: [18 §4.3](../../18-contratos-inter-equipos.md#43-tema-05--desafíos-prácticos),
> [docs/entregas/alcance-y-contrato-para-desafios-practicos.md](../../entregas/alcance-y-contrato-para-desafios-practicos.md)
> (carta dirigida a `practice-service`).

## Qué nos llama

- `POST /ai/tutor` (asistencia sincrónica, presupuesto **< 2 s**) → hoy es
  `POST /api/llm/tutor/interactions` en el contrato v1 vigente
  ([`llm-service-v1.openapi.yaml`](../../contracts/llm-service-v1.openapi.yaml)).

### Cuerpo de la solicitud ✅ (schema real: `TutorInteractionRequest`)

```json
{
  "attemptId": "b1e2c3d4-0001-4a00-8000-000000000001",
  "challengeId": "b1e2c3d4-0002-4a00-8000-000000000002",
  "courseCohortId": "b1e2c3d4-0003-4a00-8000-000000000003",
  "learnerId": "b1e2c3d4-0004-4a00-8000-000000000004",
  "message": "No entiendo por qué mi recursión no corta en el caso base",
  "riskLevel": "medium"
}
```

Header obligatorio: `Idempotency-Key` (UUID). `riskLevel` es el único campo que hoy decide el
comportamiento del guardarraíl (`high | medium | low`) — lo fija Tema 05 según el tipo de
desafío, nosotros no lo inferimos.

## Qué le damos

- Respuesta completa del tutor (200), **síncrona, sin streaming todavía** — ver
  [`pendientes.md`](pendientes.md).
- El guardarraíl anti-fuga (RF-IA-20) corre de nuestro lado antes de devolver la respuesta:
  nunca se expone la solución ni los tests ocultos (ADR-008).

### Cuerpo de la respuesta ✅ (schema real: `TutorInteractionResponse`)

```json
{
  "message": "¿Qué pasa con `n` en cada llamada recursiva? Fijate qué valor tiene justo antes de que se cumpla la condición de corte.",
  "state": "completed"
}
```

`state` es el único enum publicado hoy: `completed | blocked | unavailable`. Es más angosto que
lo que muestran los diagramas de doc 17 (que hablan de streaming y de estados intermedios) —
mientras no se fusione la adenda SSE, esto es todo lo que el contrato ejecutable promete.

## Qué pasa si esto falla

Técnica común (Resilience4j, escalera de degradación) en
[transversales del README](../README.md#resiliencia-y-manejo-de-errores-técnica-común-a-todos-los-endpoints).
Caso puntual del tutor, vía el campo `state` de la respuesta:

| `state` | Cuándo pasa | Qué ve Tema 05 |
|---|---|---|
| `completed` | El modelo respondió y pasó el guardarraíl de salida | Respuesta normal |
| `blocked` | El guardarraíl anti-fuga (ADR-008) detectó que la respuesta se acercaba a la solución esperada | Mensaje regenerado o bloqueado — **hoy no se produce en el código real** (`docs/estado-implementacion/ep-05/interactions.md`): solo hay guardarraíl de entrada implementado, el de salida está pendiente |
| `unavailable` | Se agotó la escalera de degradación (Nivel 1-3 fallaron: modelo primario, otro proveedor, modelo local) | El tutor no puede responder — presupuesto de 2 s ya se gastó en los reintentos, así que no hay margen para más de un fallback |

Si el `503`/`unavailable` se sostiene, Tema 05 tiene que decidir qué mostrarle al alumno — no
hay hoy un acuerdo escrito de UX para ese caso (relacionado con la pantalla 1 de
[`frontend-angular/pendientes.md`](../frontend-angular/pendientes.md)).

## Cierre de intento y entrega del score (evaluador) — decisión de diseño 2026-09-13

> Antes de esta decisión, el evento de cierre de intento lo publicaba Tema 03 y el score volvía
> directo a Tema 03. **Desde ahora todo el intercambio del evaluador pasa por ustedes** —
> nosotros no volvemos a hablar directo con el Motor de Desafíos. El contrato anterior queda
> retirado y documentado como tal en
> [`tema-03-motor-de-desafios/contratos.md`](../tema-03-motor-de-desafios/contratos.md).

### Qué nos dan: el cierre del intento

Ustedes nos notifican el cierre del intento del alumno — con la transcripción completa — en vez
de que lo haga Tema 03. Es el mismo evento que ya estaba definido en
[18 §3](../../18-contratos-inter-equipos.md#3-eventos-que-consumimos); lo único que cambia es
quién lo publica:

```json
{
  "evento":           "intento_cerrado",
  "version":          "1.x",
  "trace_id":         "uuid — OBLIGATORIO",
  "timestamp":        "ISO-8601",
  "curso_cohorte_id": "uuid — OBLIGATORIO",
  "intento_id":       "uuid — OBLIGATORIO",
  "alumno_id":        "uuid — OBLIGATORIO",
  "rubric_version":   "v1.1 — OBLIGATORIO",
  "transcripcion":    []
}
```

Esto además resuelve, a favor de la **Opción A** que ya proponíamos, la pregunta abierta de
quién es dueño de la transcripción alumno-tutor
([`docs/entregas/alcance-y-contrato-para-desafios-practicos.md`](../../entregas/alcance-y-contrato-para-desafios-practicos.md)
§5.4, B-2): la guardan ustedes, dueños de la UI del chat, y nos la entregan completa al cerrar el
intento.

### Qué les damos: el resultado del evaluador

Les entregamos el score (`score_de_ia_calculado`, 0-100 con desglose por dimensión) por **evento
Kafka** — no webhook, no polling — porque Kafka es el único bus de integración acordado
([00 §6](../../00-fuentes-de-verdad-y-convenciones.md#6-pares-y-comunicación)). Es el mismo
mecanismo y el mismo payload que ya estaba acordado en
[18 §2.1](../../18-contratos-inter-equipos.md#21-score_de_ia_calculado); lo único que cambia es
el consumidor:

```json
{
  "evento":           "score_de_ia_calculado",
  "version":          "1.0",
  "trace_id":         "uuid",
  "timestamp":        "ISO-8601",
  "curso_cohorte_id": "uuid",
  "intento_id":       "uuid",
  "alumno_id":        "uuid",
  "score_agregado":   78,
  "dimensiones": { "claridad": 25, "autonomia": 30, "progresion": 20, "cumplimiento": 15, "eficiencia": 10 },
  "confianza":        0.92,
  "rubric_version":   "v1.1",
  "model_id":         "claude-haiku-4.5",
  "model_version":    "batch-2025-05",
  "estado":           "aplicado"
}
```

Si el evaluador no puede completar, publicamos `score_pendiente_diferido` con `motivo` y
`reintentar_desde` — mismo payload acordado en
[18 §2.2](../../18-contratos-inter-equipos.md#22-score_pendiente_diferido), mismo cambio de
consumidor.

**Ustedes son responsables de reenviarle este resultado a Tema 03** para que aplique el
modificador de XP (PAR-05) — nosotros no le mandamos nada directo al Motor de Desafíos, ni el
score ni el evento de cierre. Cómo hacen ese reenvío (evento propio de ustedes, llamada HTTP, lo
que acuerden con Tema 03) es un problema suyo, no nuestro. Lo que no cambia: **nosotros nunca
otorgamos XP**, eso lo sigue calculando y aplicando el Motor de Desafíos.

**Sin decidir todavía** (ver [`pendientes.md`](pendientes.md)): el tópico/nombre de versión
exacto de estos eventos con el nuevo consumidor.

## Deslinde de alcance ya acordado

- **"Originalidad entre alumnos"** (comparar una entrega contra otra, o contra ediciones
  anteriores del mismo alumno) **es responsabilidad de Tema 05, no nuestra**
  ([`02-arquitectura-y-stack.md`](../../02-arquitectura-y-stack.md) línea 303). Lo nuestro es
  el perímetro anti-fuga de un único intento contra su propia solución esperada.
- La carta completa de alcance ([`docs/entregas/alcance-y-contrato-para-desafios-practicos.md`](../../entregas/alcance-y-contrato-para-desafios-practicos.md))
  ya detalla esta frontera para que Tema 05 la lea sin ambigüedad.
