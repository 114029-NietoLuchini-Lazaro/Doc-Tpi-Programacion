# Contratos históricos V1

Esta carpeta preserva OpenAPI, AsyncAPI y adendas anteriores. Sirve para entender decisiones y
compatibilidad, pero no para inventar una integración nueva.

| Archivo | Uso histórico |
|---|---|
| `llm-service-v1.openapi.yaml` | Contrato HTTP V1. |
| `llm-service-v1.asyncapi.yaml` | Eventos Kafka V1. |
| `llm-service-v2.asyncapi.yaml` | Eventos Kafka V2 (envelope con `eventVersion`, `eventType` con guiones). Es el que quedó adjunto en el Skill Hub (v4). Reemplazado por la v3 (2026-09-20). |
| `KAFKA_EVENT_STANDARD-v2-con-eventVersion.md` | Estándar Kafka anterior. Reemplazado por el del PDF `KAFKA.pdf` (`../KAFKA_EVENT_STANDARD.md`, ADR-020). |
| `llm-service-v2-golden-set.openapi.yaml` | Borrador previo de Golden Set y calibración. |
| `llm-service-v1-s1-golden-set-adenda.md` | Adenda de Sprint 1. |
| `llm-service-v1-tutor-sse-adenda.md` | Adenda SSE del tutor. |
| `llm-service-v1-moderacion-borrador.yaml` | Borrador de moderación. |

Para integrar hoy, volvé a [`../README.md`](../README.md): los contratos propuestos actuales son
[`../llm-service.openapi.yaml`](../llm-service.openapi.yaml) y
[`../llm-service.asyncapi.yaml`](../llm-service.asyncapi.yaml).
