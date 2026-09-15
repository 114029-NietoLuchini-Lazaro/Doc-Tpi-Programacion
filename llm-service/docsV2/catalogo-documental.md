# Índice global de docsV2

Este índice es el mapa de navegación completo. No resume ni duplica los documentos enlazados: cada
enlace lleva al archivo o al índice de la carpeta donde vive el contenido completo.

## Recorrido principal

| Orden | Bloque | Cuándo usarlo | Entrada |
|---:|---|---|---|
| 00 | Gobierno y evolución | Antes de decidir, cambiar una regla o resolver una contradicción. | [README](00-gobierno-y-evolucion/README.md) |
| 01 | Visión, alcance y entrega | Para entender el problema, MVP, fases y entregables. | [README](01-vision-alcance-y-entrega/README.md) |
| 02 | Arquitectura y plataforma | Antes de tocar backend, Gateway, despliegue o dependencias. | [README](02-arquitectura-y-plataforma/README.md) |
| 03 | Capacidades de IA | Para tutor, evaluación, modelos, Golden Set, rúbricas y RAG. | [README](03-capacidades-de-ia/README.md) |
| 04 | Seguridad, datos y cumplimiento | Antes de tocar prompts, datos, proveedores, cuotas o guardarraíles. | [README](04-seguridad-datos-y-cumplimiento/README.md) |
| 05 | Contratos | Antes de una comunicación HTTP o Kafka entre microservicios. | [README](contracts/README.md) |
| 06 | Operación, calidad y pruebas | Para implementar, probar, desplegar o revisar evidencia. | [README](06-operacion-calidad-y-pruebas/README.md) |
| 07 | Planificación y trabajo | Para pasar de alcance a épicas, historias, tareas y sprints. | [README](07-planificacion-y-trabajo-equipo/README.md) |
| 08 | Preguntas, investigación y sincronizaciones | Para entender antecedentes, no para sustituir una regla vigente. | [README](08-preguntas-investigacion-y-sincronizaciones/README.md) |
| 09 | Flujo de trabajo | Para ramas, PR, revisión y cierre diario. | [README](09-flujo-de-trabajo-del-equipo/README.md) |

## Índices de segundo nivel

| Necesidad concreta | Índice o documento de entrada |
|---|---|
| Entregables, evidencia y coordinación con otros equipos | [01/03 — Entregas](01-vision-alcance-y-entrega/03-entregas/README.md) |
| Reglas de Gateway, descubrimiento y comunicación micro a micro | [02/06 — Gateway y discovery](02-arquitectura-y-plataforma/06-gateway-y-discovery/README.md) |
| Golden Set, rúbricas editables y calibración | [03 — Golden Set y calibración](03-capacidades-de-ia/golden-set-y-calibracion/README.md) |
| RAG e ingesta de material | [03 — RAG e ingesta](03-capacidades-de-ia/rag-e-ingesta/README.md) |
| Estado real de código frente a lo planificado | [06/04 — Estado de implementación](06-operacion-calidad-y-pruebas/04-estado-de-implementacion/README.md) |
| Épicas, historias, tareas y sprints | [07/09 — Trabajo ejecutable](07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/README.md) |
| Plantillas oficiales de Taiga y sprint | [07/10 — Plantillas](07-planificacion-y-trabajo-equipo/10-plantillas/README.md) |
| Pendientes con cada contraparte | [07/11 — Equipos](07-planificacion-y-trabajo-equipo/11-equipos/README.md) |
| Contratos completos por microservicio/consumidor | [contracts/equipos](contracts/equipos/README.md) |
| Material importado, presentaciones, prototipos y referencias | [08/04 — Investigación y material](08-preguntas-investigacion-y-sincronizaciones/04-investigacion-y-material/README.md) |
| Contratos V1 y adendas conservados como antecedente | [contracts/historicos-y-contratos-v1](contracts/historicos-y-contratos-v1/README.md) |
| Correcciones aprobadas | [Registro de cambios](registro/README.md) |

## Referencias rápidas del MVP

| Tema | Referencia breve | Detalle |
|---|---|---|
| Precedencia y fuentes | [00 — Gobierno y fuentes](00-gobierno-y-fuentes-de-verdad.md) | [00 — Gobierno y evolución](00-gobierno-y-evolucion/README.md) |
| Alcance actual | [01 — Alcance MVP](01-alcance-mvp.md) | [01 — Visión y entrega](01-vision-alcance-y-entrega/README.md) |
| Fronteras del servicio | [02 — Arquitectura y fronteras](02-arquitectura-y-fronteras.md) | [02 — Arquitectura y plataforma](02-arquitectura-y-plataforma/README.md) |
| Ciclos de rúbrica/calibración/evaluación | [03 — Dominio y flujos](03-dominio-y-flujos-mvp.md) | [03 — Capacidades de IA](03-capacidades-de-ia/README.md) |
| Datos sensibles y protección | [04 — Seguridad y datos](04-seguridad-y-datos-sensibles.md) | [04 — Seguridad, datos y cumplimiento](04-seguridad-datos-y-cumplimiento/README.md) |
| Criterios operativos y de pruebas | [05 — Operación y pruebas](05-operacion-y-pruebas.md) | [06 — Operación, calidad y pruebas](06-operacion-calidad-y-pruebas/README.md) |
| Contrato objetivo frente a código actual | [06 — Trazabilidad y estado](06-trazabilidad-y-estado.md) | [Estado de implementación](06-operacion-calidad-y-pruebas/04-estado-de-implementacion/README.md) |

## Cobertura de `docs/` en V2

El contraste archivo por archivo contabilizó 308 artefactos funcionales de V1 y todos tienen
destino bajo V2. `.DS_Store` fue excluido por ser metadato local. Las familias migradas son:

| Familia de V1 | Archivos | Destino V2 |
|---|---:|---|
| Archivos de raíz | 44 | Bloques 00–09 y referencias breves de la raíz. |
| Contratos y equipos | 25 | `contracts/` y `07.../11-equipos/`. |
| Entregas | 11 | `01.../03-entregas/`. |
| Épicas, historias, tareas y sprints | 124 | `07.../09-epicas-historias-tareas-sprints/`. |
| Arquitectura Gateway y estado de implementación | 49 | `02.../06-gateway-y-discovery/` y `06.../04-estado-de-implementacion/`. |
| Material importado, presentaciones, prototipos, recomendaciones y referencias | 50 | `08.../04-investigacion-y-material/`. |
| Plantillas | 5 | `07.../10-plantillas/`. |

Las diferencias de nombre responden a la organización por tema. El contenido de V1 no se descartó:
cuando una regla o schema se consolidó, quedó una fuente canónica y una referencia desde el lugar
anterior.
