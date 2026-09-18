# Laboratorio de integración local

Este entorno prueba contratos reales de `llm-service` sin exigir todavía el Gateway, Courses ni el
frontend oficiales.

```text
Workbench Angular -- /api/** --> gateway-mock -- /api/llm/** --> llm-service --> PostgreSQL
                                             \-- /api/courses/** --> courses-mock
```

## Qué es real y qué se simula

- **Real:** controllers, autorización funcional, casos de uso, JDBC, Flyway, PostgreSQL, adaptadores
  HTTP de `llm-service` y contratos OpenAPI.
- **Simulado por red:** autenticación/borde del Gateway y `courses-service` (MockServer).
- **Fuera del laboratorio:** frontend oficial, IdP, Eureka, Kafka y proveedores LLM con credenciales
  de producción.

Desde `llm-service/`:

```bash
docker compose -f compose.yaml -f compose.workbench.yaml up --build
```

Abrir `http://localhost:4200/docente`. El navegador sólo usa rutas relativas; nunca conoce hosts
internos, tokens M2M ni headers de identidad. Para detenerlo:

```bash
docker compose -f compose.yaml -f compose.workbench.yaml down
```

El Gateway mock descarta la identidad enviada por el navegador, agrega identidad delegada,
`traceparent` y `X-Request-Id`, y conserva el path completo. `llm-service` consulta membresías
mediante su cliente HTTP real usando un Bearer M2M; los casos de Cursos están en
`demo/courses/expectations.json`.

El Workbench no es el frontend final. Sirve como consumidor plug-and-play de referencia para el
equipo Angular: las rutas, esquemas, paginación, idempotencia, `If-Match`, SSE y Problem Details se
obtienen exclusivamente de [`../../contracts/llm-service.openapi.yaml`](../contracts/llm-service.openapi.yaml).
