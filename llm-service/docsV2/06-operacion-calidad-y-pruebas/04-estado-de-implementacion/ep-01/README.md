# EP-01 · Plataforma, contratos e integración — estado

> Fichas fuente: [`docs/historias/ep-01/`](../../../07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/historias/ep-01/README.md). Auditoría de
> código: [`llm-service/CORRECCIONES-SUGERIDAS.md`](../codigo-ejemplo/fuentes/CORRECCIONES-SUGERIDAS.md)
> ítems 16–22 (primera vez que estos habilitadores se contrastan contra código; hasta el
> 2026-09-12 ninguna ficha de EP-01 tenía marcador de estado).

## Índice

| ID | Título | Estado | Nota en una línea |
|---|---|---|---|
| [H01](h01.md) | ADR de arquitectura y convenciones técnicas | 🟢 | ADR-001 y ADR-002 aceptados el 2026-09-19 (aprobación directa, sin PR formal); `ArchitectureTest` en verde |
| [H02](h02.md) | Entorno reproducible con un comando | 🟢 | CA1–CA4 y T7 verificados con evidencia real 2026-09-16; se agregó volumen persistente de Postgres (gap real) |
| [H03](h03.md) | Esqueleto transversal del servicio | 🟡 | Cliente Eureka configurado; 401/403 separados con Problem Details; eco de X-Request-Id verificado |
| [H04](h04.md) | Esquema inicial versionado con auditoría | 🟢 | Migración `V1` cumple lo que pide la ficha |
| [H08](h08.md) | Contrato OpenAPI y mock del golden set publicados | 🟢 | Contrato v2 sincronizado con lo construido; mock levantable con Prism (`docs/contracts/MOCK.md`) |
| [H07](../../../07-planificacion-y-trabajo-equipo/09-epicas-historias-tareas-sprints/historias/ep-01/h07.md) *(propuesta)* | Esqueleto de mensajería Kafka con deduplicación | 🟢 | Outbox + relay, consumidor con dedup y dead-letter en tabla; `EventOutboxKafkaFlowIT` (EmbeddedKafka) en verde; apagado por defecto (`llm.kafka.enabled=false`) hasta el broker real |
| [H09](h09.md) | Suite de pruebas y guía de demo de S1 | 🟢 | `mvn verify` 2026-09-20: 678 unitarios + 66 de integración, 0 fallas; gate JaCoCo cumplido (93,9 % instrucciones, 94,6 % líneas; umbral 90 %); guía de demo y script de reinicio listos |

## Pendiente para cerrar EP-01 al 100 %

- **CI:** el workflow `llm-service-ci.yml` se retiró el 2026-09-19 (`28a8b5c1`). Sin él, H03·CA6, H06·CA1 y H06·CA5 solo se cumplen localmente con `mvn verify`.
- **H03·CA1:** registro en el Eureka y ruteo por el Gateway compartidos sin verificar de punta a punta (infraestructura de la plataforma).
- **H07:** integración con el broker Kafka real y nombres de tópico definitivos.
