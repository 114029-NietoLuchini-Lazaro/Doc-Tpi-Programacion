# 39 — Servicios Docker actuales

La composición base ejecuta el backend real y PostgreSQL. La composición Workbench agrega
únicamente simuladores de frontera y el frontend temporal.

| Servicio | Archivo | Estado |
|---|---|---|
| `postgres` (`postgres:16-alpine`) | `compose.yaml` | Real, base exclusiva de `llm-service`. |
| `llm-service` | `compose.yaml` | Real, sin puerto de negocio publicado al host. |
| `gateway-mock` (Nginx) | `compose.workbench.yaml` | Simulado, único punto HTTP del laboratorio (`localhost:8080`). |
| `courses-mock` (MockServer) | `compose.workbench.yaml` | Simulado, reemplaza `courses-service`. |
| `workbench` (Angular) | `compose.workbench.yaml` | Consumidor temporal en `localhost:4200`. |

No se documenta `pgvector`, Redis, MinIO o un Kafka local como dependencia actual: no forman parte
de `compose.yaml` ni del código ejecutable de `main`. Eureka y el API Gateway reales los provee la
plataforma; este repositorio sólo contiene el cliente Eureka y el simulador local del borde.

## Comandos

```bash
# Backend real aislado
docker compose -f compose.yaml up --build

# Laboratorio completo: backend real + Gateway/Courses simulados + Workbench
docker compose -f compose.yaml -f compose.workbench.yaml up --build

# Detener el laboratorio
docker compose -f compose.yaml -f compose.workbench.yaml down
```

El Workbench usa rutas relativas `/api/**`. `gateway-mock` conserva `/api/llm/**`, inyecta identidad
delegada y correlación, y enruta `/api/courses/**` al fixture MockServer. Ningún perfil ni catálogo
de demo debe convertirse en atajo para que el navegador llegue directo al backend.

Para el recorrido completo, casos de membresía y límites entre real/simulado, consultar el
[laboratorio de integración local](07-laboratorio-integracion-local.md). Para contratos, consultar
[`docsV2/contracts/`](../contracts/README.md).
