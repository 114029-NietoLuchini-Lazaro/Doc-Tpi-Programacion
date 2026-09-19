# Tema 05 — Desafíos Prácticos — pendientes

> Este documento es la fuente completa de lo pendiente con Tema 05. Antecedentes:
> [17 §7.3](../../../contracts/90-mapa-de-integracion-historico.md#73-quién-nos-bloquea-y-a-quién-bloqueamos) (N1, N3),
> [08 B-1](../../../00-gobierno-y-evolucion/02-decisiones-y-pendientes.md), [20-backlog-y-sprints.md](../../01-backlog-y-sprints.md) E12-02.

## 🔴 Cruzado — la solución esperada del desafío

Sin esto el guardarraíl anti-fuga (RF-IA-20) no tiene contra qué comparar. Falta definir
endpoint, verbo y payload. Hay resistencia esperable: le estamos pidiendo a Tema 05 que exponga
algo que hoy consideran interno y sensible.

**Nuestra propuesta ya escrita** (doc 08 B-1): que Tema 05 exponga un endpoint que devuelva la
solución esperada **solo a nosotros**, solo para comparación — nunca la almacenamos, la usamos
y la descartamos. Ofrecer esa garantía por escrito destraba la conversación.

**Estado a la fecha de `20-backlog-y-sprints.md`:** sin resolver — la salvaguarda corre hoy
contra una solución mock, y la integración real queda anotada como deuda.

**🟡 Propuesta de cuerpo — no acordado, solo para arrancar la conversación:**

```json
// GET (hipotético) {tema-05}/challenges/{challengeId}/expected-solution
// Llamado por nosotros, con nuestro JWT M2M — nunca al revés.
{
  "challengeId": "b1e2c3d4-0002-4a00-8000-000000000002",
  "language": "java",
  "expectedSolution": "public int factorial(int n) { ... }",
  "hiddenTestsSummary": "3 casos borde: n=0, n=1, overflow"
}
```

Ningún campo de este ejemplo está cerrado — ni el verbo, ni si viaja por HTTP o por evento, ni
si incluye `hiddenTestsSummary` o solo el código de referencia. Se pone acá para que la sesión
de integración tenga algo concreto para tachar o corregir, no para presentarlo como decidido.

## 🔴 Cruzado — evento de ediciones y ejecuciones de tests del IDE

Alimenta el 30% del score (dimensión autonomía). Si no se pide ahora, no va a existir.

**Sin decidir:** si la fuente es Tema 05 (IDE) o Tema 06 (sandbox de ejecución) —
`11-glosario-y-metadata.md` deja explícito que puede ser cualquiera de los dos y no está
resuelto.

## 🟡 Nuevo — cómo le reenvían ustedes el resultado a Tema 03

Nosotros les entregamos el score por evento Kafka (`score_de_ia_calculado`, ver
[`contratos.md`](contratos.md)) — pero qué usan para que Tema 03 aplique el modificador de XP
(evento propio suyo, llamada HTTP, lo que sea) es una definición entre ustedes y Tema 03, no algo
que nosotros necesitemos cerrar. Se anota acá porque es la pieza que le faltaba a
[I-04](../../../contracts/90-mapa-de-integracion-historico.md#-i-04--el-resultado-sale-por-cuatro-caminos-distintos) para
cerrarse del todo: de nuestro lado ya no hay cuatro caminos, hay uno (a ustedes); el salto de
ustedes a Tema 03 queda fuera de nuestro contrato.

## 🟡 Interno, en desarrollo — streaming SSE del tutor

**Hoy NO implementado.** El código actual (EP-05) solo tiene el camino síncrono completo con
guardarraíles; streaming quedó fuera de esta pasada
(`docs/estado-implementacion/ep-05/README.md`).

El contrato ya está escrito y listo para fusionar:
[`llm-service-v1-tutor-sse-adenda.md`](../../../contracts/historicos-y-contratos-v1/llm-service-v1-tutor-sse-adenda.md)
(patrón Buffer Interceptor, eventos `token`/`hold`/`segment`/`blocked`/`done`/`error`). Pero
**no se fusiona al contrato vigente hasta que se cierre I-10** (streaming: propagar o revertir
la decisión en el resto de los documentos) **y Tema 05 acuerde consumir SSE**.

Por eso, aunque sea trabajo nuestro, hay que avisarles antes de construirlo — no es un cambio
que se pueda lanzar sin que el otro lado sepa que va a dejar de recibir una respuesta completa
de una sola vez.

**Nota aparte, sin bloquear esta conversación:** tampoco existe todavía una ficha de historia
formal para EP-05 (`docs/historias/ep-05/` no existe hoy) — el código se adelantó a la ficha,
igual que pasó con golden set/calibración. Se anota acá como contexto, no como algo que
dependa de Tema 05.

## 🟡 Nuevo — UX del tutor `unavailable`

Sin acordar: qué le mostramos al alumno cuando el tutor agota la escalera de degradación y
responde `state: unavailable`. No hay copy, ni pantalla, ni reintento sugerido definidos. Surge
al documentar resiliencia en `contratos.md` — no bloquea código, pero sí a Front End para armar
la pantalla 1.

## Colisión de vocabulario

"Umbral 70%" significa cosas distintas para cada equipo: para nosotros es el piso de similitud
del guardarraíl anti-fuga; para Tema 05 es el umbral de originalidad entre entregas de
alumnos. Ver [transversales del README](../README.md#glosario-de-colisiones-de-vocabulario) y
[`11-glosario-y-metadata.md`](../../../00-gobierno-y-evolucion/03-glosario-y-metadata.md).

## 🔴 Kafka — contrato de `practice-events` a cerrar (2026-09-19)

Al revisar [`llm-service.asyncapi.yaml`](../../../contracts/llm-service.asyncapi.yaml) v2.0.0 contra el código
(`PracticeAttemptClosedListener`) quedan estas preguntas para Tema 05. Es la lista de arranque:
se agregan acá todos los temas nuevos que salgan de la charla.

- [ ] **Message Key de `practice-events`:** la define el productor y hoy figura "Pendiente" en el
  AsyncAPI. Confirmar qué usan (¿`attemptId`? ¿`courseCohortId`?) y el orden que garantiza.
- [ ] **`AttemptClosed` — campos y versión:** confirmar `attemptId`, `courseCohortId`, `learnerId`,
  `transcript` (forma de cada mensaje del transcript, tamaño máximo) y `eventVersion` de partida.
- [ ] **Topic y `eventType` de nuestros eventos de score:** hoy publicamos en `evaluation-events`
  (`SCORE-CALCULATED` / `SCORE-DEFERRED`). Confirmar que consumen ese topic y que el nombre les sirve
  (sigue pendiente lo de "tópico/nombre de versión" de la sección de contrato).
- [ ] **Payload de `ScoreCalculated` / `ScoreDeferred`:** ya está en el AsyncAPI como **provisorio**
  (`score`, `dimensions`, `evaluator`, `rubricVersionId`; `reason` y `retryFrom` en el diferido).
  Falta que Tema 05 lo valide como consumidor.
- [ ] **Fuente del evento de ediciones/tests del IDE:** ¿Tema 05 o Tema 06 (sandbox)? Ya listado arriba;
  si es Tema 06 hay que sumarlos a la conversación.
- [ ] **Reintentos y DLQ:** qué esperan que hagamos ante un `AttemptClosed` inválido o duplicado
  (hoy: idempotencia por `eventId` + `DeadLetterPublisher`) y quién monitorea la DLQ.
- [x] **Scope del tutor:** alineado a `llm.tutor.interact` (el registrado en el Gateway y el que exige el
  código). Se corrigieron los documentos que decían `llm.tutor.invoke`; hay que avisarle a Tema 05.
- [x] **Evaluador por Kafka (nuestro lado):** `PracticeAttemptClosedListener` ya dispara la evaluación
  contra el fake y publica `SCORE-CALCULATED`/`SCORE-DEFERRED` en `evaluation-events`
  (`AttemptEvaluationService`, con IT sobre Kafka embebido). Queda que Tema 05 valide el payload.
- [ ] **Cohorte → curso → rúbrica:** el evento trae `courseCohortId` y no hay mapa a curso, así que se
  evalúa siempre con la plantilla institucional. Definir de dónde sale la rúbrica activa del curso.
- [ ] _(agregar acá lo que surja)_

## 🟢 Integración en modo test — disponible hoy

Tema 05 puede integrar el tutor (HTTP) y el evaluador (Kafka) contra el `fake` (sin modelo real) sin
cambios de contrato.
Alcance y límites en la sección "Modo de prueba" de
[`tema-05-desafios-practicos.md`](../../../contracts/equipos/tema-05-desafios-practicos.md#modo-de-prueba--integrar-contra-el-tutor-sin-modelo-real-2026-09-19).
Falta que ellos confirmen que les sirve arrancar así.
