# EP-02 · AI Gateway, modelos y resiliencia — estado

> Ficha fuente: [`docs/historias/ep-02/h01.md`](../../../07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/historias/ep-02/h01.md) (LLM-EP02-H01, ex-H10). H10 se cerró el
> 2026-09-12 portando `LlmGateway`/`GroqAdapter` de
> [`codigo-ejemplo/ms-evaluacion-llm`](../codigo-ejemplo/ms-evaluacion-llm.md) (carpeta ya
> eliminada, ver [`codigo-ejemplo/README.md`](../codigo-ejemplo/README.md)) a
> `llm-service/domain/ai/` + `infrastructure/ai/`.

## Índice

| ID | Título | Estado | Nota en una línea |
|---|---|---|---|
| [H10](h10.md) | Puerto del proveedor de modelos (AI Gateway) y fake para pruebas | 🟢 | 6 de 6 tareas — portado de `codigo-ejemplo/ms-evaluacion-llm` |
| H02 | Proveedor real (Groq) detrás del puerto | 🟡 | `GroqModelAdapter` (langchain4j) seleccionado por `function_model_config`. **Verificado en vivo el 2026-09-19** con `openai/gpt-oss-20b` sobre el compose real: el tutor responde en español y de forma socrática (0,9-2,4 s) y no entrega el código aunque se lo pidan; el evaluador pasa el schema y da 90 a un intento bueno y 25 a uno malo; el camino `SCORE-DEFERRED` funciona. Para el evaluador, el adaptador saca el JSON de respuestas envueltas en ```json o con texto alrededor (`JsonObjectExtractor`); el tutor no se toca. El `modelId` de `PUT /model-assignments/{function}` ahora se respeta: el adaptador de Groq usa el modelo asignado a la función y `GROQ_MODEL` queda solo como valor por defecto (antes lo ignoraba y el registro de uso anotaba un modelo que no era el invocado). 🟡 **El evaluador devuelve el mismo valor en las cinco dimensiones** (copia un ancla de la rúbrica), lo que no discrimina: hay que calibrarlo contra el golden set antes de confiar en el desglose. La verificación en vivo es manual: CI no tiene `GROQ_API_KEY` |
| [H03](h03.md) | Resiliencia síncrona: reintentos, circuit breaker, presupuesto, uso | 🟡 | Construida **con mocks/hardcodeo** (2026-09-19): reintentos + breaker por proveedor reales; presupuesto y registro de uso en memoria — detalle en [`h03.md`](h03.md) |
| [model-deployments](model-deployments.md) | Catálogo de despliegues de modelo (sin ficha) | 🔴 | `ModelDeploymentController.listAdapters` devuelve proveedores hardcodeados — **sin tocar**, es otro catálogo (por curso), no el `function_model_config` de H10 |

**Consecuencia:** H10 ya no bloquea a EP-04. **Actualizado 2026-09-13:** la calibración de curso
**sí está conectada** al puerto — `CalibrationRunWorker.dispatch()` ya invoca
`ModelInvocationService` por cada caso del golden set (cerrado por T7, ver
[`ep-04/s03-h01.md`](../ep-04/s03-h01.md), ahora 🟢). Sigue siempre contra el **fake**: el hueco
que queda es exclusivamente [`ep-02/h02.md`](../../../07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/historias/ep-02/h02.md) (proveedor real).

## Auditoría de código 2026-09-19 (EP-02 completa)

Estado contra los criterios de aceptación de [`epicas/ep-02.md`](../../../07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/epicas/ep-02.md):

| Criterio | Estado |
|---|---|
| Flujo completo (límites → proveedor → validación → resultado/error controlado; admin cambia modelo sin redeploy) | 🟢 `ModelInvocationService` + `ModelAssignmentController` |
| 100 % de llamadas pasan por la capa | 🟢 `GatewayExecutor` es el único punto de ejecución: lo usan `ModelInvocationService`, `EmbeddingInvocationService` y el chat de prueba admin (`ProviderCredentialController`, sin reintentos). Cubierto por test; no verifiqué que ningún otro camino llame a un proveedor por fuera |
| Rechazo de respuestas fuera de formato | 🟡 solo `TUTOR` y `EVALUATOR`; `MODERATOR`/`GENERATOR` sin schema |
| Registro de costo/latencia/errores por llamada | 🟡 mock en memoria (`GatewayUsageLog`, últimas 500); tokens reales del proveedor cuando los informa (Groq) y estimados chars/4 si no; precios en `llm.gateway.pricing` (yml); `llm_usage_records` (V17) sigue solo para `ADMIN_TEST`/`EVALUATION` |
| Presupuesto y cuotas por función y período | 🟡 mock: `GatewayBudget` con límites diarios hardcodeados en memoria; `QuotaRegistry` (por alumno) sigue aparte y sin conectar al gateway |
| Observabilidad y alertas | 🟡 endpoints `GET /admin/gateway/usage` y `/calls`, métricas Micrometer `llm.gateway.*`; alertas solo por **log** (`ALERTA ...`), sin canal real |
| Documentación operativa | 🟢 [`runbook-ai-gateway.md`](runbook-ai-gateway.md) |

**Sigue mockeado/hardcodeado (a reemplazar):** límites de presupuesto (hardcodeados en `GatewayBudget`),
contadores y bitácora en memoria (se pierden al reiniciar, no escalan a más de una réplica), tokens
estimados cuando el proveedor no los informa (el adaptador `fake` y los de admin sin uso). Política de
reintentos/breaker y precios ya son configurables por `llm.gateway.*`.
