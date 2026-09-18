# Mock del contrato llm-service — Golden Set y Calibración (v2)

> **Historia:** `LLM-EP01-H05` (ex-H08) — Contrato OpenAPI y mock del golden set publicados (10 h).  
> **Criterio de Aceptación CA2:** El simulador se levanta con un comando documentado y responde según el contrato.  
> **Contrato de referencia:** [`llm-service-v2-golden-set.openapi.yaml`](llm-service-v2-golden-set.openapi.yaml).  
> **Propósito:** Permite a `admin-service` y otros consumidores de la plataforma avanzar en su desarrollo e integración desacoplada sin depender del despliegue real del servicio.

---

## 1. Opción Standalone (Prism — Mock HTTP directo del contrato)

Recomendada para pruebas rápidas de contrato, integración en CI de otros equipos o desarrollo sin base de datos ni Docker.

### Prerrequisito
Disponer de Node.js (npx se incluye por defecto) o Docker.

### Comando para levantar el mock (desde la raíz del repo)

```bash
npx --yes @stoplight/prism-cli mock docs/contracts/llm-service-v2-golden-set.openapi.yaml --port 4010
```

El simulador queda disponible en:  
`http://localhost:4010/api/llm`

### Alternativa con Docker (sin requerir Node local)

```bash
docker run --rm -p 4010:4010 -v "${PWD}/docs/contracts:/tmp/contracts" stoplight/prism:4 mock /tmp/contracts/llm-service-v2-golden-set.openapi.yaml --host 0.0.0.0
```

---

## 2. Opción Simulador de Desarrollo Local (Perfil Workbench)

El repositorio incluye un perfil de desarrollo simulado para pruebas locales del backend con datos controlados en memoria.

### Comando

```bash
docker compose -f compose.yaml -f compose.workbench.yaml up
```

### Características del modo Workbench
- Activa `SPRING_PROFILES_ACTIVE=workbench`.
- Habilita `WorkbenchDemoCatalog` con cursos preconfigurados (Programación III, Paradigmas de Programación).
- Simula la identidad del docente sin requerir el API Gateway real ni un Identity Provider M2M.

---

## 3. Ejemplos de Peticiones contra el Mock Standalone (Puerto 4010)

### A. Listar Cursos Autorizados
```bash
curl -s http://localhost:4010/api/llm/courses \
  -H "Authorization: Bearer mock-token"
```

### B. Listar Golden Sets de un Curso
```bash
COURSE_ID="00000000-0000-0000-0000-000000000010"
curl -s http://localhost:4010/api/llm/courses/${COURSE_ID}/golden-sets \
  -H "Authorization: Bearer mock-token"
```

### C. Crear un Borrador de Golden Set
```bash
COURSE_ID="00000000-0000-0000-0000-000000000010"
curl -s -X POST http://localhost:4010/api/llm/courses/${COURSE_ID}/golden-sets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer mock-token" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"name": "Golden Set Inicial 2026"}'
```

### D. Publicar Versión de Golden Set
```bash
COURSE_ID="00000000-0000-0000-0000-000000000010"
VERSION_ID="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
curl -s -X POST http://localhost:4010/api/llm/courses/${COURSE_ID}/golden-sets/${VERSION_ID}/publish \
  -H "Authorization: Bearer mock-token" \
  -H "Idempotency-Key: 22222222-3333-4444-5555-666666666666"
```

### E. Crear Siguiente Versión
```bash
COURSE_ID="00000000-0000-0000-0000-000000000010"
VERSION_ID="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
curl -s -X POST http://localhost:4010/api/llm/courses/${COURSE_ID}/golden-sets/${VERSION_ID}/next-version \
  -H "Authorization: Bearer mock-token" \
  -H "Idempotency-Key: 33333333-4444-5555-6666-777777777777"
```

---

## 4. Alcance y Límites del Mock

| Aspecto | Comportamiento en el Mock | Comportamiento en Producción |
|---|---|---|
| **Persistencia** | Sin persistencia (stateless: genera respuestas válidas conformes a schema) | PostgreSQL con Flyway |
| **Idempotencia** | No verifica unicidad de `Idempotency-Key` | Valida clave y rechaza duplicados |
| **Autorización** | Acepta cualquier Bearer token sintético | Valida JWT M2M con scopes y tenancy en Gateway |
| **Calibración** | Retorna estado QUEUED/RUNNING de ejemplo | Ejecuta runner asíncrono con métricas PAR-14 |

---

## 5. Mock de Moderación de chat (EP-08)

**Contrato:** [`llm-service-v1-moderacion.openapi.yaml`](llm-service-v1-moderacion.openapi.yaml) (v1.1.0). Para `chat-service` y `notification-service`: integran sin depender del `llm-service` real.

```bash
npx --yes @stoplight/prism-cli mock docs/contracts/llm-service-v1-moderacion.openapi.yaml --port 4011
```

Con Docker: `docker run --rm -p 4011:4010 -v "${PWD}/docs/contracts:/tmp/contracts" stoplight/prism:4 mock /tmp/contracts/llm-service-v1-moderacion.openapi.yaml --host 0.0.0.0`

Se elige la respuesta con `Prefer: example=<nombre>` (o `Prefer: code=<status>`):

```bash
H=(-H 'X-Principal-Type: service' -H 'X-Service-Id: chat-service' -H 'X-Service-Scopes: moderation:decide' -H 'Content-Type: application/json')
BODY='{"message_id":"m-1","course_id":"curso-42","sender_id":"alumno-1","sender_role":"student","text":"hola"}'

curl -s -X POST localhost:4011/moderation/v1/decisions "${H[@]}" -H 'Prefer: example=allow' -d "$BODY"                  # ALLOW
curl -s -X POST localhost:4011/moderation/v1/decisions "${H[@]}" -H 'Prefer: example=block' -d "$BODY"                  # BLOCK + incident_id
curl -s -X POST localhost:4011/moderation/v1/decisions "${H[@]}" -H 'Prefer: example=pendingReviewDegradado' -d "$BODY" # PENDING_REVIEW (contextual caído)
curl -s -X POST localhost:4011/moderation/v1/decisions "${H[@]}" -H 'Prefer: example=pendingTimeout' -d "$BODY"         # PENDING (timeout 800 ms)
curl -s -X POST localhost:4011/moderation/v1/appeals -H 'X-Principal-Type: user' -H 'X-User-Id: alumno-1' -H 'Content-Type: application/json' -H 'Prefer: code=409' -d '{"incident_id":"eee5cf4d-2903-42dd-bcc8-fead34b06450","appeal_reason":"Era un mensaje legitimo con enlaces."}'
```

Diferencia con el real: el mock es stateless (no detecta spam ni crea incidentes; devuelve el ejemplo elegido) y **no valida identidad**. El servicio real se prueba con el runbook `docs/sprints/sprint-4/runbook-verificacion-moderacion.md`.
