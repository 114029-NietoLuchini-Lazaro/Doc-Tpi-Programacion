# Preguntas abiertas — auditoría de historias (todas las épicas)

> Generado por la auditoría historia-por-historia de `docs/historias/ep-XX/` iniciada el
> 2026-09-13. Cada ítem es una pregunta de producto, proceso o decisión técnica que **no**
> corresponde resolver por cuenta propia durante la auditoría — queda acá para tratarla con el
> Product Owner / referente de producto / equipo, historia por historia, sin bloquear el avance
> de la auditoría misma.
>
> Formato: `[EP-XX·Hyy] Pregunta — por qué importa (fuente)`. Se marca `✅ resuelta` con fecha
> cuando alguien la cierre, no se borra (queda como historial de decisiones).

---

## Transversales (afectan a varias épicas / a todo el backlog)

- [x] ✅ **2026-09-13 [Sprint 0]** ¿Ya ocurrió el Sprint 0 formal (Planning Poker, historia
  canónica elegida y estimada)? **Resuelto:** sí — los DoR/DoD se leyeron y aceptaron por el
  equipo en reunión, y las épicas EP-01/EP-03 junto con las historias de S1 ya quedaron cargadas
  en Taiga (evidencia de que el Sprint 0 se ejecutó). Acción residual, no bloqueante: el acta
  [`../sprints/sprint-0/sprint-0.md`](../sprints/sprint-0/sprint-0.md) sigue con valores por
  volcar (puntos de la historia canónica, permalinks de Taiga, cierre firmado por
  referente/facilitador).
- [ ] **Nombrar al "referente de producto"** en todas las fichas que todavía dicen *"a nombrar en
  Sprint 0"* (EP-01 H01–H07, EP-02 H01–H03, EP-03 H01–H05 y las que se sumen). Es un **rol único
  del equipo** (voz del cliente — no un PO externo), no un nombre por historia.

## EP-01 · Plataforma, contratos e integración

- [x] ✅ **2026-09-13 [EP-01·H01]** ¿El ADR de convenciones técnicas ya se redactó y mergeó, o sigue
  el hueco de `estado-implementacion/ep-01/h01.md` (🔴 "no existe ningún ADR")? **Resuelto:** los
  ADRs ya están actualizados, charlados y aceptados por el equipo en la última reunión.
  **No se implementa ArchUnit** (CA4/CA5: límite del `domain` y env-vars documentadas): se cumple
  con la **convención interna + revisión manual en PR**.
- [x] ✅ **2026-09-13 [EP-01·H03]** ¿El colapso de todo a `403` (nunca `401`,
  `estado-implementacion/ep-01/h03.md`) es decisión deliberada o hueco? **Resuelto:** la ficha
  [G03](ep-01/h03.md) es la fuente y está publicada en Taiga: token sin scope → `401`, identidad
  delegada falsificada/malformada → `403`. El comportamiento actual (todo `403`) es un **hueco de
  implementación a corregir en el código**; no se reescribe CA4/Escenario 2.
- [ ] **[EP-01·H03]** ¿Existe hoy un pipeline de CI real para `llm-service` (CA6)?
  **2026-09-13:** la herramienta de CI **todavía no está definida** por el equipo; la auditoría no
  encontró workflow en `.github/workflows/`.
- [ ] **[EP-01·H05]** ¿Con qué herramienta se levanta el mock del golden set "con un solo comando"
  (CA2)? **2026-09-13:** **Prism se descarta** (la sección "Estrategia de autonomía" salió de H05;
  la ficha local `ep-01/h05.md:97-101` aún la referencia — alinear cuando se edite la ficha).
  El stack de testing queda en **JUnit 5 + Mockito** (confirmado en 02/23/26). La herramienta del
  mock del contrato standalone queda **por definir** (candidatas: WireMock / Mockoon).
- [x] ✅ **2026-09-13 [EP-01·H05]** ¿La adenda S1 del contrato ya está revisada y firmada por
  `admin-service`? **Resuelto:** sí — `admin-service` **aprueba la adenda y los campos**.
- [x] ✅ **2026-09-13 [EP-01·H06]** ¿Qué herramienta de cobertura se usa para el gate de CI
  (CA2/CA5)? **Resuelto:** **JaCoCo**, con umbral obligatorio del **90 %** según
  [24](../24-convenciones-cobertura.md) (toda mención a 90–95 % queda superada por el doc).
- [ ] **[EP-01·H06]** ¿Existe algún borrador de la guía de demo paso a paso, o hay que escribirla
  desde cero? **2026-09-13:** no se identificó borrador; **H06 ya está publicada en Taiga** y en
  proceso de asignación. Al implementarla, decidir si hay borrador previo o se escribe desde cero
  (CA4 sigue sin evidencia).
- [ ] **[EP-01]** Falta una historia futura (propuesta como [H07](ep-01/h07.md), pendiente de alta
  en `35`) que cubra el CA de épica "publica/consume eventos sin duplicarlos".
  **2026-09-13:** referencia **actualizada al plan vigente de 5 sprints** ([38](../38-plan-de-5-sprints.md);
  los sprints S3/S6/S10/S19 eran del horizonte viejo). No se subió a Taiga con las historias de S1;
  **en qué sprint entra queda a consultar con el grupo**.

## EP-02 · AI Gateway, modelos y resiliencia

- [ ] **[EP-02·H02]** ¿El código de referencia `GroqAdapter` preservado en
  [`codigo-ejemplo/ms-evaluacion-llm.md`](../estado-implementacion/codigo-ejemplo/ms-evaluacion-llm.md)
  sigue completo/compilable? Confirmar antes de comprometer la estimación de ~15–22 h.
  - Resuelto: ✅ **2026-09-13.** Es el único hueco de código propio del alcance obligatorio de la
    cátedra ([38 · Parte 1](../38-plan-de-5-sprints.md)) — prioridad ya confirmada como Must, no
    hace falta re-confirmarla.
- [ ] **[EP-02·H02]** ¿Groq es la elección definitiva de proveedor real, o solo la referencia
  heredada del código de ejemplo? **2026-09-13:** **Groq NO es el proveedor definido** — todavía
  hay propuestas de proveedores sin fijar del todo; en total se usarían/usarán
  **~4 proveedores o modelos distintos**. El ADR (EP-01·H01) debería nombrar la selección firme
  cuando se cierre.
- [ ] **[EP-02]** Falta una historia (propuesta como [H03](ep-02/h03.md), pendiente de alta en
  `35`) que cubra la restricción de épica de reintentos/circuit breaker — H01/H02 solo resuelven
  el timeout de una llamada individual.

## EP-03 · Golden set y referencia humana

- [x] ✅ **2026-09-13 [EP-03·H02]** La designación de "historia canónica" quedó sobre una ficha (H02, ex-H06)
  cuyo código real fue reemplazado por H05 (`605f381`). **Resuelto:** se estima Fibonacci contra **H05**
  (código vigente), no contra H02 — son tamaños de esfuerzo distintos.
- [x] ✅ **2026-09-13 [EP-03·H03]** ¿La pantalla de `llm-workbench` ya se migró para consumir los endpoints de
  H04/H05 (`/api/llm/courses/{courseId}/...`)? **Resuelto:** sí — consume exclusivamente los endpoints
  nuevos course-scoped (`golden-sets`, `golden-set-imports`, `synthetic-golden-set-cases`,
  `cases/publish/next-version`) y no usa el contrato viejo de H01/H02 (las rutas `golden-sets` del router
  son placeholder sin uso).
- [ ] **[EP-03·H04]** *(ya señalada en la propia ficha)* ¿El invariante de pesos 30/25/20/15/10
  ([23 §4.2](../23-plan-construccion-producto-llm.md)) aplica solo a la rúbrica base de
  plataforma, o también a cada rúbrica por curso? **Hallazgo 2026-09-13:** `RubricValidator` solo
  exige 5 dimensiones únicas con peso en (0,100] y suma 100; el patrón fijo vive solo en la seed de
  base (V1: 30/25/20/15/10), ya que V2 permite pesos positivos ≤100 libres por curso.
- [ ] **[EP-03·H05]** *(ya señalada en la propia ficha)* ¿Quién crea/publica la base de plataforma
  que `copyFromPublishedBase` necesita leer? Hoy no existe ningún endpoint que la genere.
- [x] ✅ **2026-09-13 [EP-03]** Faltaba la historia de **doble puntuación independiente y
  resolución de discrepancias** — redactada como propuesta en
  [`ep-03/h06.md`](ep-03/h06.md), pendiente de alta en `35` y de estimación en Refinamiento.
- [ ] **[EP-03]** Ningún CA de H01/H02/H05 mide el KPI de épica "las consultas se resuelven en
  menos de 100 ms" — todas dicen "baja latencia" en prosa, sin umbral verificable.
- [ ] **[EP-03]** `synthetic-golden-set` y `eligible-interactions-golden-set` no tienen ficha de historia
  redactada. **Confirmado 2026-09-13:** el código **sí existe** (controllers + servicios placeholder +
  tests; el workbench consume `synthetic-golden-set-cases`) y ambos figuran 🔴 placeholder en
  `estado-implementacion/ep-03/` — falta solo la ficha HU (deuda documental, no de código).

## EP-04 · Calibración y gobernanza del modelo

> La carpeta ya es ejemplar en autodocumentar sus propios huecos (H01 trae una sección
> "⚠️ Diferencia de alcance con la épica" y H02 es la respuesta propuesta) — menos preguntas
> nuevas que en otras épicas, quedan las que siguen sin decisión.

- [x] ✅ **2026-09-13 [EP-04·H01]** El archivo mezcla el checklist de CA/Escenarios BDD (que el template exige
  dejar como `- [ ]` sin marcar y sin código inline para pegar en Taiga) con anotaciones de
  estado reales (`[x]`, 🟢, nombres de test entre backticks). **Resuelto:** la separación de
  "criterio" vs "evidencia de que ya se cumplió" se hace **al momento de pegar la ficha en Taiga**
  — la ficha `h01.md` no se modifica (regla ya escrita en el propio template).
- [x] ✅ **2026-09-13 [EP-04·H02]** ¿Se reutiliza `calibration_runs` con `course_id` nulo para representar
  "plataforma", o conviene una tabla separada? **Resuelto:** tabla **separada**
  (`platform_calibration_runs`) — `calibration_runs` hoy exige `course_id` y `requireTeacher`, y
  `active_calibrations` usa el curso como clave. Decisión registrada; falta reflejar la nota en
  `h02.md` (Impacto en los datos) cuando se trabaje esa ficha.
- [ ] **[EP-04·H03]** El tiempo límite de vencimiento por inactividad no tiene valor por defecto
  — a definir con el Product Owner en Refinamiento (la ficha ya lo marca, recomienda arrancar
  conservador). **Nota 2026-09-13:** queda registrado como default propuesto **60 días**,
  conservador, hasta tener datos reales de uso.
- [ ] **[EP-04·H03] — hallazgo nuevo de esta auditoría:** la épica exige explícitamente
  *"cambiar de modelo dispara una recalibración con alertas"*, pero H03 solo dispara vencimiento
  por nueva rúbrica, nuevo golden set o tiempo límite — **cambiar el despliegue de modelo no está
  en la lista de disparadores de vencimiento**. **Decisión 2026-09-13:** agregarlo como **CA +
  Escenario 4** en `h03.md` (cambio de despliegue de modelo vence la calibración activa con
  motivo registrado) — pendiente de aplicar cuando se trabaje esa ficha; acá queda registrado.
- [x] ✅ **2026-09-13 [EP-04·H04]** ¿Qué canal usa el evento de "curso con evaluaciones frenadas" — el mismo bus
  de `calibracion_fuera_de_tolerancia.v1` u otro propio? **Resuelto:** topic **propio**,
  `evaluaciones_frenadas_por_calibracion.v1` (convención del bus: un evento = un topic; correlación
  en headers de Kafka, publicación por outbox). Documentar en el AsyncAPI al implementar.
- [ ] **[EP-04] — hallazgo nuevo:** el KPI de épica *"toda habilitación o cambio de modelo queda
  registrada de forma permanente"* no tiene ningún CA dedicado en H01/H02 que verifique el
  registro de auditoría de habilitación/cambio de modelo en sí (más allá del audit log genérico
  de H04·EP-01). **Decisión 2026-09-13:** agregar un **CA + escenario en H02** (qué modelo, motivo,
  quién, cuándo) — pendiente de aplicar en la ficha; acá queda registrado.

---

## EP-05 · Tutor seguro y guardarraíles

- [x] ✅ **2026-09-13 [EP-05·H03]** La ficha dependía de **`challenges-service`** para consultar
  si un desafío está abierto/cerrado ("el equipo del `challenges-service` debe confirmar el
  endpoint o evento..."), pero [00 §6](../00-fuentes-de-verdad-y-convenciones.md), cambiado el
  **2026-09-13**, dice explícitamente que **`llm-service` dejó de comunicarse directo con
  `challenges-service`** — todo pasa ahora por `practice-service`. Corregida la sección de
  Dependencias de [`ep-05/h03.md`](ep-05/h03.md) para consultar el estado del desafío vía
  `practice-service`.
- [x] ✅ **2026-09-13 [EP-05]** Faltaba la historia de **cuota por alumno** (KPI de épica
  "aviso claro al superar la cuota") — redactada como propuesta en
  [`ep-05/h04.md`](ep-05/h04.md), reutilizando el mecanismo de `EP-07·H02`/`H03` en vez de
  duplicarlo. Pendiente de alta en `35` y de que producto fije el valor numérico del límite.
- [ ] **[EP-05] — hallazgo nuevo:** la épica exige *"si el tutor no está disponible, el alumno
  puede seguir su intento sin asistencia, y eso queda registrado"* — ninguna ficha cubre qué
  registra `llm-service` cuando el tutor está caído/degradado (más allá del guardarraíl normal).
- [ ] **[EP-05·H02/H03]** Solapamiento parcial de escenarios entre H02 (lista/historial de
  conversación) y H03 (mismo historial + control de pertenencia): ambas prueban "historial
  ordenado" y "conversación inexistente/no accesible". Es una capa de endurecimiento válida
  (H03 depende de H02), pero conviene revisar en Refinamiento si no convendría fusionar el CA de
  ownership dentro de H02 en vez de repetir el escenario base en una tercera ficha (criterio I de
  INVEST — ver [29 §3](../29-guia-catedra-historias-de-usuario.md)).
- [ ] **[EP-05·H02]** `ConversationRepository`/`MessageRepository` (JDBC directo) siguen en 0 % de
  cobertura de integración real por bloqueo de Docker/Testcontainers en la sesión que las
  construyó — no dar el CRUD por verificado de punta a punta hasta correr esa integración.
- [ ] **[EP-05·H01]** CA6 (rechazo sin autenticación de servicio válida) está cubierto por código
  (`TutorGatewayAuthorization`) pero sin test dedicado a nivel de esta ficha todavía.

---

## EP-06 · Evaluación, score y auditoría académica

> Calidad de redacción notablemente alta (H04/H05/H06 traen wireframes, ejemplos JSON exactos y
> distinguen bien "pendiente por falta de calibración" de "pendiente por falla del evaluador").
> Todavía son borradores (⚪), la carpeta completa arrancó en 🔴 (cero código).

- [x] ✅ **2026-09-13 [EP-06·H04/H05]** Faltaba todo mecanismo para sacar una apelación de
  `PENDING_REVIEW` (bloqueaba el cierre de curso de H06 para siempre). Corregido: **H05** ahora
  acepta un `appealId` opcional en el override (lo resuelve como `ADJUSTED`) y agrega el endpoint
  `POST .../appeals/{appealId}/resolve` para que el docente confirme la nota sin cambiarla
  (`UPHELD`) — mismo patrón que [`EP-08·H04`](ep-08/h04.md). **H04** referencia la corrección.
  Nuevos CA6–CA8/Escenarios 5–6 en H05.
- [x] ✅ **2026-09-13 [EP-06]** Ninguna pareja líder confirmada en el catálogo para EP-06 (todas las fichas
  dicen "a asignar"). **Resuelto:** el catálogo asigna P4 + P1 a EP-06; H04/H05 quedaron con P4 y H06 con P5.
  H01–H03 de las fichas siguen "a asignar" (inconsistencia interna a limpiar al asignar la pareja).
- [ ] **[EP-06·H01]** Decisión de diseño abierta: ¿la evaluación "pendiente por intento recién
  cerrado, no arrancada" (EP-06) es un estado distinto o el mismo que "pendiente por falta de
  calibración" (EP-04, `pending_evaluations`)? Ya señalada en la propia ficha como riesgo.
- [ ] **[EP-06·H02/H03]** El schema de `data` de `score_de_ia_calculado.v1` y
  `score_pendiente_diferido.v1` sigue siendo un `Envelope` vacío en el AsyncAPI — falta acordarlo
  con quien consume esos eventos (probablemente el servicio de cálculo académico) antes de
  implementar la publicación. Ya señalado en ambas fichas, no inventado por ellas — correcto.
- [ ] **[EP-06·H03]** Intervalo entre reintentos y límite antes de escalar a "pendiente
  prolongada" sin valor por defecto — a definir en Refinamiento (ya señalado en la ficha).
- [ ] **[EP-06·H04]** El plazo de apelación depende de que `courses-service` exponga esa
  configuración por curso — si no está listo, la propia ficha ya propone un plan de contingencia
  razonable ("siempre abierto" en una primera iteración, marcado explícitamente como
  provisional). Buena práctica a imitar en otras fichas con la misma dependencia externa.
- [ ] **[EP-06·H05]** La verificación de rol `TEACHER` por curso (no solo por plataforma) es la
  pieza de seguridad más crítica de la ficha y depende de un contrato con
  identidad/`courses-service` todavía no confirmado — la ficha ya advierte no lanzar con un
  chequeo de rol "plano" sin esa verificación.
- [ ] **[EP-06·H06]** Riesgo ya anotado en la propia ficha: si H01 o H04 no están construidas
  todavía, este endpoint devuelve `canClose: true` siempre, aunque haya pendientes reales — no
  desplegar sin marcarlo explícitamente como comportamiento provisional.

---

## EP-07 · Operación, cuotas y observabilidad

> Calidad de redacción excelente (H01–H03): ejemplos JSON completos, CA con valores numéricos
> exactos, BDD verificable literalmente. Mismo nivel que EP-06.

- [x] ✅ **2026-09-13 [EP-07]** Faltaban las 3 fichas de S10 indexadas sin archivo — redactadas:
  [`h04.md`](ep-07/h04.md) (recuperar trabajos detenidos), [`h05.md`](ep-07/h05.md) (salud útil
  sin el proveedor), [`h06.md`](ep-07/h06.md) (prueba de carga y backup/restore).
- [ ] **[EP-07·H03] / [EP-02] — hallazgo nuevo, cruce entre épicas:** `LLM-S09-H03` (EP-07) ya
  especifica **reintentos con backoff exponencial + jitter y circuit-breaker por función** contra
  el proveedor de modelos — es prácticamente el mismo mecanismo que la propuesta
  [`EP-02·H03`](../ep-02/h03.md) (armada en esta misma auditoría) para resolver la restricción de
  resiliencia síncrona de EP-02. **Hay que decidir en Refinamiento cuál construye el decorador de
  resiliencia real** (recomendado: EP-02, por ser la épica dueña conceptual de "resiliencia
  síncrona de la llamada al modelo") **y que la otra lo reutilice** en vez de reimplementarlo —
  si se construyen las dos por separado, quedan dos circuit-breakers independientes vigilando la
  misma llamada al proveedor, lo cual es peor que no tener ninguno (estados inconsistentes entre
  ambos). Ya anoté esta corrección en la propia ficha de `EP-02·H03`.
- [ ] **[EP-07·H01]** Depende de `LLM-S03-H11` (proveedor real conectado, ex-numeración de
  `EP-02·H02`) — coherente, pero confirmar que la referencia se actualice al ID vigente
  `LLM-EP02-H02`.
- [x] ✅ **2026-09-13 [EP-07·H01]** Riesgo ya anotado en la ficha: el costo es **estimado** (tokens reportados ×
  precio configurado), no viene de facturación real del proveedor — aclarar esto en cualquier
  demo para no generar expectativas de precisión contable. **Resuelto:** la ficha ya lo cubre en
  Notas (`h01.md`, "No se accede a APIs de facturación") y en Riesgos (mitigación: documentar que
  es estimado); resta solo reiterarlo en la demo.

---

## EP-08 · Moderación integrada (F2)

> Misma calidad alta que EP-06/EP-07 (contratos JSON exactos, wireframes, CA medibles). Buena
> práctica a destacar: **H04 (docente resuelve incidente) sí cierra correctamente el ciclo de
> apelación** — resuelve `CONFIRMED`/`REVERSED`, notifica al alumno y dispara el desbloqueo. Es
> exactamente el patrón que le falta a EP-06 (ver arriba) — vale la pena usar H04 como plantilla
> al corregir EP-06·H04/H05.

- [x] ✅ **2026-09-13 [EP-08]** Faltaban las 2 fichas de S13 indexadas sin archivo — redactadas:
  [`h05.md`](ep-08/h05.md) (degradación cuando el clasificador contextual falla),
  [`h06.md`](ep-08/h06.md) (retención y purga de evidencia).
- [ ] **[EP-08·H01]** El contrato define `409 Conflict` para "mismo `message_id` con texto
  distinto", pero el CA_negativo_2 describe el mismo caso devolviendo la decisión original en vez
  de 409 — hay una pequeña inconsistencia entre el cuerpo de ejemplo (`RESPONSE 409`) y el CA/BDD
  correspondiente (que no menciona código de estado); conviene unificar cuál es el comportamiento
  exacto antes de implementar.
- [ ] **[EP-08·H02]** Los umbrales de detección "ofensivo" son configurables por curso (política
  del docente) — falta el endpoint o mecanismo de configuración en sí; no hay ficha que lo cubra
  (¿vive acá, en un futuro H de EP-08, o en el panel de EP-07?).

---

## EP-09 · RAG y consulta de material (F3)

> Dos fichas, honestas sobre su propio alcance reducido ("adelanto", embeddings fake, PDF como
> `BYTEA` en vez de referencia externa — todo declarado explícitamente, no escondido).

- [ ] **[EP-09] — hallazgo nuevo:** el KPI de épica *"el asistente encuentra el material correcto
  en al menos el 85 % de las consultas de prueba"* no tiene ningún CA en H01/H02 que lo mida —
  ambas fichas verifican mecanismos (citas, abstención, filtro por cohorte) pero no un corpus de
  prueba con métrica de recall. Falta definir ese conjunto de prueba y su umbral antes de dar la
  épica por cerrada, aunque no bloquea construir H01/H02 tal como están.
- [ ] **[EP-09·H01]** Decisión ya tomada y documentada como simplificación temporal: PDF guardado
  como `BYTEA` en Postgres en vez de referencia externa (contradice el catálogo original de S15,
  "no guardar binarios") — a revisar antes de pasar de adelanto a compromiso de sprint real.
  Ya está en el propio estado de implementación, no es un hallazgo nuevo, solo lo dejo listado.
- [ ] **[EP-09·H02]** La autorización con scope propio para RAG queda "pendiente de decisión de
  producto final" — ya señalado en la ficha, sin resolver todavía.

---

## EP-10 · Personalización y agente (F3)

- [x] ✅ **2026-09-13 [EP-10]** Faltaban las 2 fichas de "@agente" indexadas sin archivo —
  redactadas: [`h03.md`](ep-10/h03.md) (mención citada y moderada),
  [`h04.md`](ep-10/h04.md) (validación de mención real, anti-bucle entre bots).
- [ ] **[EP-10·H02] — hallazgo nuevo, inconsistencia de stack:** la ficha especifica la cola
  durable del job de generación como **"Celery + Redis (AOF)"** — Celery es un framework de colas
  de **Python**, incompatible con el stack declarado en el ADR de EP-01 (Java 21 / Spring Boot 3).
  El propio patrón de "estado persistido + worker que retoma por checkpoint" ya existe funcionando
  en el repo en Java puro (`CalibrationRunWorker` de EP-04, `@Scheduled` + tabla de estado) — lo
  más consistente es reusar ese mismo patrón acá en vez de introducir Celery/Redis. Revisar antes
  de comprometer esta historia a un sprint: probablemente quedó de una referencia genérica sin
  adaptar al stack real del proyecto.
- [ ] **[EP-10·H01]** Dependencia de `LLM-S09-H03` (cuotas) — coherente con el resto del backlog,
  sin objeciones nuevas.

---

## Cómo usar este documento

Cada vez que el equipo resuelva un ítem, marcarlo con fecha y decisión tomada (no borrar la línea,
así queda historial). Los ítems marcados **"hallazgo nuevo"** son los que esta auditoría encontró
por primera vez (no estaban ya señalados en la ficha o en `estado-implementacion/`); el resto son
preguntas que la propia documentación ya dejaba abiertas y esta auditoría solo consolidó en un
único lugar.

### Patrón transversal — ✅ cerrado el 2026-09-13

Los tres patrones que se repetían en varias épicas ya quedaron resueltos con fichas redactadas
(todas siguen pendientes de **alta formal en `35`, estimación en Refinamiento y revisión del
equipo** — redactarlas no las convierte en compromiso de sprint):

1. **Índices de README que prometían más fichas de las que existían** — EP-07 (h04–h06), EP-08
   (h05–h06) y EP-10 (h03–h04) ya están escritas.
2. **Ciclos de apelación/revisión que no cerraban** — EP-06·H04/H05 corregidas con el vínculo
   apelación↔override, siguiendo el patrón que ya usaba EP-08·H04.
3. **Historias que faltaban porque un CA de épica no tenía ninguna ficha** — EP-01·H07, EP-02·H03,
   EP-03·H06 y EP-05·H04 redactadas como propuestas.

**Lo único que sigue pendiente de acción real** (no de redacción): que el equipo revise las 11
fichas nuevas/corregidas de esta pasada, las estime, les asigne pareja/referente de producto, y
decida en qué sprint entra cada una — nada de esto se inventa desde una auditoría de documentos.
