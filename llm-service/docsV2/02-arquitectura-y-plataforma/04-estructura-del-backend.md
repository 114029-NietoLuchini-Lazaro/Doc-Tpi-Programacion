# 37 — Estructura real del backend

> Esta foto se verificó contra `main` local el 2026-09-18. Los nombres de capas de documentos
> históricos no deben usarse para crear carpetas nuevas.

```text
llm-service/
├── app/                         aplicación Spring Boot ejecutable
├── provider-spi/                puerto común para proveedores LLM
├── provider-openai-compatible/  adaptador OpenAI-compatible
├── provider-anthropic/          adaptador Anthropic
├── provider-gemini/             adaptador Gemini
├── llm-workbench/               consumidor Angular temporal
├── demo/                        Gateway/Courses simulados
├── compose.yaml                 PostgreSQL + app privados
└── docsV2/                      documentación canónica
```

El `pom.xml` raíz es un reactor Maven con esos cinco módulos. La aplicación está en:

```text
app/src/main/
├── java/ar/edu/utn/frc/tup/piv/llm/
│   ├── adapter/in/web/          controllers bajo /api/llm/**
│   ├── adapter/in/messaging/    consumidores de eventos
│   ├── adapter/out/ai/          adaptadores a proveedores
│   ├── adapter/out/http/        clientes HTTP, incluido Courses
│   ├── adapter/out/messaging/   publicación de eventos
│   ├── adapter/out/persistence/ JDBC y repositorios
│   ├── application/             casos de uso, puertos y workers
│   ├── domain/                  reglas de negocio sin I/O
│   └── configuration/           configuración Spring
└── resources/db/migration/      migraciones Flyway
```

Los tests viven en `app/src/test/java/...`. No existe un árbol ejecutable `src/main` en la raíz de
`llm-service`; toda incorporación nueva debe respetar el módulo `app` y los `provider-*`.

El frontend no llama a `app` directamente. En local atraviesa `gateway-mock`; el cliente
`GatewayCoursesMembershipClient` también sale por Gateway con M2M. MockServer permanece fuera del
backend y representa sólo la dependencia Courses.

| Cambio | Ubicación |
|---|---|
| Endpoint HTTP | `app/.../adapter/in/web` |
| Caso de uso | `app/.../application` |
| Regla sin I/O | `app/.../domain` |
| JDBC o migración | `adapter/out/persistence` + `resources/db/migration` |
| Proveedor LLM | módulo `provider-*` + `adapter/out/ai` |
| Cliente a otro microservicio | `adapter/out/http` |
| Kafka | `adapter/in|out/messaging` |
