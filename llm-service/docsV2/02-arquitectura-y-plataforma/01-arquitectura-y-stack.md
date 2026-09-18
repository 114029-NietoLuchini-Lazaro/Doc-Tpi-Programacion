# 02 — Arquitectura y stack vigente

Este documento describe el código que existe en `main` local. Los diseños `/ai/**`, `trace_id`,
pgvector y los módulos de infraestructura que no aparecen en el árbol actual son antecedentes o
trabajo futuro, no instrucciones para el MVP ejecutable.

## Frontera

```text
Frontend / Workbench
        │ /api/**
        ▼
API Gateway ── /api/llm/** ──► llm-service ──► PostgreSQL 16
                                      │
                                      └── Gateway + M2M ──► courses-service
```

En local, `gateway-mock` reemplaza el Gateway y `courses-mock` reemplaza Courses. El servicio,
autorización funcional, persistencia, migraciones y adaptadores siguen siendo reales. El Gateway
preserva el path completo, sanea headers aportados por el navegador e inyecta identidad delegada.

## Stack comprobado

| Área | Implementación actual |
|---|---|
| JVM/backend | Java 21, Spring Boot 3.5, Spring MVC y validation |
| Persistencia | Spring JDBC + PostgreSQL 16 + Flyway |
| Mensajería | Spring Kafka como dependencia del módulo; broker real lo provee la plataforma |
| Descubrimiento | Cliente Eureka; servidor lo provee la plataforma |
| Proveedores | Reactor Maven `provider-spi`, `provider-openai-compatible`, `provider-anthropic`, `provider-gemini` |
| Front de referencia | Angular Workbench fuera del producto final |
| Correlación | `traceparent` y `X-Request-Id` en headers; no `trace_id` en JSON |
| Errores | Problem Details RFC 7807 |

No se debe agregar una dependencia de `pgvector` sólo porque exista documentación de RAG: esa
capacidad está planificada y requiere migración, código y pruebas antes de cambiar la imagen de
PostgreSQL.

## Responsabilidades

- `llm-service`: tutor, guardarraíles, rúbricas, Golden Set, calibración, evaluación del uso de IA
  y configuración de proveedores.
- `courses-service`: cohortes y membresía/rol; `llm-service` lo consulta por Gateway con M2M.
- `practice-service`: práctica, intento y transcripción; invoca el tutor con scope M2M.
- Gateway: autenticación, autorización de borde, ruteo, correlación e identidad delegada.

Los contratos ejecutables y sus estados son la fuente normativa en
[`docsV2/contracts/`](../contracts/README.md). La estructura física está en
[04 — Estructura del backend](04-estructura-del-backend.md).
