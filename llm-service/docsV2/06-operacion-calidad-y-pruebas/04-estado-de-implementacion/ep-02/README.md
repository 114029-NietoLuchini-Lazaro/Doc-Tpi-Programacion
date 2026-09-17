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
| [model-deployments](model-deployments.md) | Catálogo de despliegues de modelo (sin ficha) | 🔴 | `ModelDeploymentController.listAdapters` devuelve proveedores hardcodeados — **sin tocar**, es otro catálogo (por curso), no el `function_model_config` de H10 |

**Consecuencia:** H10 ya no bloquea a EP-04. **Actualizado 2026-09-13:** la calibración de curso
**sí está conectada** al puerto — `CalibrationRunWorker.dispatch()` ya invoca
`ModelInvocationService` por cada caso del golden set (cerrado por T7, ver
[`ep-04/s03-h01.md`](../ep-04/s03-h01.md), ahora 🟢). Sigue siempre contra el **fake**: el hueco
que queda es exclusivamente [`ep-02/h02.md`](../../../07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/historias/ep-02/h02.md) (proveedor real).
