# EP-04 · Calibración y gobernanza del modelo — estado

> Ficha fuente: [`docs/historias/ep-04/`](../../historias/ep-04/README.md) (ya trae el estado
> embebido, escrita *a posteriori*). Auditoría:
> [`verificacion-v2-golden-set-calibracion.md`](../../entregas/verificacion-v2-golden-set-calibracion.md)
> §3, [`llm-service/CORRECCIONES-SUGERIDAS.md`](../../../CORRECCIONES-SUGERIDAS.md)
> ítem 2.

## Índice

| ID | Título | Estado | Nota en una línea |
|---|---|---|---|
| [s03-h01](s03-h01.md) | Correr y activar una calibración de curso con métrica PAR-14 | 🟢 | T7 (conectar con H10) cerrada — `CalibrationEvaluationRunner` corre cada run contra el fake y lo transiciona a `PASSED`/`FAILED` |
| [pending-evaluations-gate](pending-evaluations-gate.md) | Compuerta que difiere una evaluación sin calibración activa (sin ficha, confirmado EP-04 el 2026-09-12) | 🟢 (para lo que cubre) | Solo resuelve "falta calibración", no "evaluador caído" — ver [`ep-06`](../ep-06/README.md) |
