# Mock del contrato `llm-service`

El mock de integración no reemplaza al backend: el backend y PostgreSQL se ejecutan de verdad.
Sólo se simulan las fronteras que pertenecen a otros equipos.

## Laboratorio recomendado

```bash
docker compose -f compose.yaml -f compose.workbench.yaml up --build
```

- Workbench: `http://localhost:4200/docente`.
- Gateway mock: `http://localhost:8080`.
- `/api/llm/**`: Gateway mock → `llm-service` real.
- `/api/courses/**`: Gateway mock → Courses MockServer.

El navegador usa rutas relativas y no construye headers de identidad ni tokens M2M. Nginx descarta
los headers sensibles aportados por el cliente, inyecta identidad delegada de desarrollo,
`traceparent` y `X-Request-Id`, y conserva el path completo. `llm-service` consulta membresías
por su cliente HTTP real con el Bearer M2M configurado para el laboratorio.

## Mock standalone del schema

Para consumidores que sólo necesitan validar forma HTTP, Prism puede servir el contrato canónico:

```bash
npx --yes @stoplight/prism-cli mock docsV2/contracts/llm-service.openapi.yaml --port 4010
```

Este mock es stateless y no prueba autorización, persistencia ni reglas de negocio. Para probar el
flujo real de integración debe usarse el laboratorio Docker.

Los fixtures de Courses están en `demo/courses/expectations.json` y cubren membresía válida,
rol incorrecto, inexistente, error y demora.
