# 39 — Servicios de Docker: qué se levanta hoy y qué queda preparado

> Inventario de todo lo que `llm-service/compose.yaml` y `llm-service/compose.workbench.yaml`
> pueden levantar: lo que ya corre porque hay código que lo usa, y lo que está preparado y
> **comentado** en el compose para el día que exista ese código — así no hay que
> redescubrir la configuración (imagen, puerto, variables) cuando llegue el momento.
> Cómo se usan los dos archivos día a día: [README §Cómo levantar el servicio](../README.md#cómo-levantar-el-servicio)

## 1. Activos hoy

| Servicio | Compose | Imagen | Por qué está activo |
|---|---|---|---|
| **`postgres`** | `compose.yaml` | `pgvector/pgvector:pg16` | Única base de datos del servicio (docs/12 §2) **y** motor de la extensión `vector` que usan los chunks de RAG (`V14__tutor_conversations_and_rag.sql`, `CREATE EXTENSION IF NOT EXISTS vector`). |
| **`llm-service`** | `compose.yaml` | build de `Dockerfile` (Java 21 / Spring Boot 3) | El backend. Sin `ports` publicado por defecto: en la plataforma real solo el API Gateway expone puertos (ADR-015, docs/08 · [gateway-y-discovery/01](06-gateway-y-discovery/01-principios-y-reglas-de-red.md)). |
| **`workbench`** | `compose.workbench.yaml` (se suma con `-f`) | build de `llm-workbench/Dockerfile.dev` (Angular 21) | Frontend docente de prueba (evaluador, rúbricas, golden set, calibración, tutor+RAG), publicado en `localhost:4200`. Solo para demo/desarrollo local, no para la integración real. |

**Por qué `pgvector/pgvector:pg16` y no `postgres:16-alpine`.** `docs/26` Parte 4 documenta
`postgres:16-alpine` como la imagen del stack, pero esa imagen no trae la extensión `vector`
compilada — y sin ella, la migración de EP-09 falla al arrancar. `pgvector/pgvector:pg16` es
la misma base (Postgres 16) con la extensión ya instalada; es la imagen que efectivamente hace
falta para que `docker compose up` funcione de punta a punta.

## 2. Preparados y comentados en `compose.yaml`

Ninguno de los tres tiene código en `llm-service/src` que lo use todavía (sin dependencia en
`pom.xml`, sin cliente instanciado). Se dejan comentados —imagen, puerto y variables ya
resueltas— para no perder tiempo de config cuando el trabajo que los necesita empiece.

| Servicio | Imagen propuesta | Para cuándo | Qué lo activa |
|---|---|---|---|
| **`redis`** | `redis:7-alpine` (AOF) | Si la cola interna crece más de lo que Postgres `SKIP LOCKED` aguanta, o hace falta caché de retrieval/cuotas con TTL. | Hoy está **descartado a propósito**: "a 120 usuarios probablemente no haga falta Redis" (docs/06 Parte 2 · docs/12 §3). Revisar esa cuenta antes de descomentarlo. |
| **`kafka`** | `apache/kafka:3.8.0` (KRaft, un solo nodo) | S6 (docs/25), cuando el servicio tenga outbox + workers que publiquen `score_de_ia_calculado.v1` (docs/18) contra el bus real del Tema 11. | Agregar `spring-kafka` al `pom.xml` y el publisher/consumer — el broker local sirve para probar esa integración sin depender del broker de cátedra. |
| **`minio`** | `minio/minio:latest` | Si en algún momento se decide sacar el PDF de Postgres. | Hoy los PDF van como `BYTEA` en `rag_documents` — decisión explícita, no un default por pereza (docs/estado-implementacion/ep-09/ingesta.md, "Qué NO se portó"). Activarlo implica escribir el cliente S3 y migrar esa columna. |

## 3. Lo que a propósito no está ni comentado

**Eureka (Service Discovery) y API Gateway.** `docs/25` es explícito: *"no existe un servidor
Eureka/Gateway ejecutable en este repositorio"* — los opera el Tema 01/11, y esa suite de
pruebas *"se incorporará junto con los módulos reales, no como simulación local"*. Fabricar acá
un Eureka o un Gateway de mentira daría una falsa sensación de integración probada. Lo único que
sí vive en este repo es el lado cliente:

- `spring-cloud-starter-netflix-eureka-client` ya está en el `pom.xml`.
- `EUREKA_URL` ya existe como variable documentada en [`.env.example`](../../.env.example)
  apuntando por defecto a `http://localhost:8761/eureka/`. No está en `compose.yaml` todavía
  porque no hay nada real del otro lado para apuntarle dentro de la red del compose.

Cuando el proyecto de cátedra provea esos contenedores, `EUREKA_URL` se agrega a `compose.yaml`
apuntando al nombre de red de ese servicio — no hay nada más que preparar de este lado.

## 4. Cómo se combinan los dos archivos

| Comando | Qué levanta |
|---|---|
| `docker compose up --build` | Solo `postgres` + `llm-service`, sin perfil `workbench`, sin puertos de negocio publicados. |
| `docker compose -f compose.yaml -f compose.workbench.yaml up --build` | Lo anterior, más `workbench` en `localhost:4200`, y le agrega `SPRING_PROFILES_ACTIVE=workbench` a `llm-service` (CORS para `localhost:4200`, identidad docente fija — `WorkbenchCorsConfiguration`, `WorkbenchDemoCatalog`). |

Detalle línea por línea de cada variable de entorno: [`.env.example`](../../.env.example)
Smoke test automatizado que valida que este compose realmente levanta sano:
[`scripts/smoke-compose.sh`](../../scripts/smoke-compose.sh) (docs/25, fila "Docker Compose").
