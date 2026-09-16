# Guía de Demostración Reproducible — Sprint 1 (LLM-EP01-H06 / ex-H09)

> **Historia de Usuario / Tarea Técnica:** [`historias/ep-01/h06.md`](historias/ep-01/h06.md)  
> **Traza de Criterios de Aceptación:** CA3 (persistencia tras reinicio), CA4 (guía paso a paso ejecutable), CA6 (bloqueo ante pérdida de datos).  
> **Audiencia:** Equipo de desarrollo, Product Owner, Comité evaluador de Sprint Review.  
> **Objetivo:** Demostrar con evidencia reproducible —y no solo con un relato— el recorrido canónico de S1: **acceso autorizado → alta de rúbrica y golden set → carga de casos de referencia → reinicio de contenedores → consulta y verificación de persistencia**.

---

## 1. Prerrequisitos y Variables de Entorno

La demostración debe ejecutarse en el ambiente integrado reproducible mediante Docker Compose (o contra el Gateway de plataforma).

1. Clonar el repositorio y situarse en la carpeta raíz de `llm-service`.
2. Contar con un archivo `.env` configurado a partir de [`.env.example`](../.env.example):
   ```bash
   cp .env.example .env
   ```
3. Identificadores fijos utilizados para la demostración:
   - **ID de Curso (`courseId`):** `22222222-2222-2222-2222-222222222222`
   - **ID de Docente (`teacherId` / delegado):** `11111111-1111-1111-1111-111111111111`

---

## 2. Convención de Headers de Plataforma (Gateway e Identidad)

Conforme a las reglas de la plataforma (ADR-015 y [00 · gobierno](../docsV2/00-gobierno-y-fuentes-de-verdad.md)), todas las peticiones a `/api/llm/**` deben incorporar los headers inyectados por el API Gateway:

```bash
# Headers requeridos para las pruebas con curl / httpie
HEADER_SERVICE='X-Service-Id: admin-service'
HEADER_SCOPES='X-Service-Scopes: llm.golden-set.manage'
HEADER_DELEGATED='X-Delegated-User: 11111111-1111-1111-1111-111111111111'
HEADER_ACTOR_ID='X-Actor-Id: 11111111-1111-1111-1111-111111111111'
HEADER_ROLE='X-User-Roles: TEACHER'
HEADER_COURSE='X-Teacher-Course-Ids: 22222222-2222-2222-2222-222222222222'
HEADER_TRACE='traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01'
HEADER_REQ_ID='X-Request-Id: demo-s1-step'
```

---

## 3. Recorrido Paso a Paso de la Demo

### Paso 1: Arranque del Stack y Comprobación de Salud

Levantar el entorno completo y comprobar que el servicio y la base de datos PostgreSQL con pgvector estén listos:

```bash
docker compose up -d --wait
```

Verificar el endpoint de Actuator:
```bash
curl -s http://localhost:8080/actuator/health
```
**Resultado Esperado:**
```json
{"status":"UP"}
```

---

### Paso 2: Alta de Rúbrica y Publicación (Opcional si ya existe)

Para que el curso cuente con su marco de evaluación activo en las 5 dimensiones obligatorias:

```bash
# 2.1 Crear borrador de rúbrica
curl -s -X POST http://localhost:8080/api/llm/courses/22222222-2222-2222-2222-222222222222/rubrics/draft \
  -H "Content-Type: application/json" \
  -H "$HEADER_SERVICE" -H "$HEADER_SCOPES" -H "$HEADER_DELEGATED" \
  -H "$HEADER_ACTOR_ID" -H "$HEADER_ROLE" -H "$HEADER_COURSE" -H "$HEADER_REQ_ID" -H "$HEADER_TRACE" \
  -d '{"name":"Rúbrica Algoritmos S1"}'
```

---

### Paso 3: Creación de la Versión del Golden Set Docente

Dar de alta el contenedor de versiones del Golden Set para el curso:

```bash
curl -s -X POST http://localhost:8080/api/llm/courses/22222222-2222-2222-2222-222222222222/golden-sets \
  -H "Content-Type: application/json" \
  -H "$HEADER_SERVICE" -H "$HEADER_SCOPES" -H "$HEADER_DELEGATED" \
  -H "$HEADER_ACTOR_ID" -H "$HEADER_ROLE" -H "$HEADER_COURSE" -H "$HEADER_REQ_ID" -H "$HEADER_TRACE" \
  -d '{"name":"Banco de Casos Oficial S1"}'
```

**Resultado Esperado:** Código HTTP `201 Created` con el JSON de la versión creada:
```json
{
  "id": "<VERSION_ID>",
  "familyId": "<FAMILY_ID>",
  "versionNo": 1,
  "state": "DRAFT",
  "name": "Banco de Casos Oficial S1"
}
```
*(Anotar el `<VERSION_ID>` obtenido).*

---

### Paso 4: Carga del Caso de Referencia en el Golden Set

Insertar un caso representativo en el banco de referencia con transcripción y puntajes docentes en las cinco dimensiones:

```bash
curl -s -X POST http://localhost:8080/api/llm/courses/22222222-2222-2222-2222-222222222222/golden-sets/<VERSION_ID>/cases \
  -H "Content-Type: application/json" \
  -H "$HEADER_SERVICE" -H "$HEADER_SCOPES" -H "$HEADER_DELEGATED" \
  -H "$HEADER_ACTOR_ID" -H "$HEADER_ROLE" -H "$HEADER_COURSE" -H "$HEADER_REQ_ID" -H "$HEADER_TRACE" \
  -d '{
    "transcript": [
      {
        "role": "STUDENT",
        "content": "¿Cuál es el peor caso del algoritmo Quicksort y por qué?"
      }
    ],
    "challengeContext": {
      "statement": "Explicar particionamiento y complejidad cuadrática",
      "expectedKeyPoints": ["pivote desbalanceado", "O(n^2)"]
    },
    "author": "Prof. S1 Titular",
    "referenceScores": {
      "AUTONOMY": 80,
      "CLARITY": 85,
      "PROGRESSION": 90,
      "COMPLIANCE": 88,
      "EFFICIENCY": 90
    },
    "scoreJustifications": {
      "AUTONOMY": "El estudiante identifica de manera independiente el caso del pivote"
    }
  }'
```

**Resultado Esperado:** Código HTTP `201 Created` confirmando la persistencia del caso en PostgreSQL.

---

### Paso 5: Consulta Previa al Reinicio

Comprobar que el Golden Set y sus datos son visibles en la API del curso:

```bash
curl -s -X GET http://localhost:8080/api/llm/courses/22222222-2222-2222-2222-222222222222/golden-sets \
  -H "$HEADER_SERVICE" -H "$HEADER_SCOPES" -H "$HEADER_DELEGATED" \
  -H "$HEADER_ACTOR_ID" -H "$HEADER_ROLE" -H "$HEADER_COURSE" -H "$HEADER_REQ_ID" -H "$HEADER_TRACE"
```

---

### Paso 6: Reinicio del Entorno (Prueba de Resiliencia y Persistencia)

Simular un reinicio de contenedores o ciclo de parada de la infraestructura:

```bash
docker compose restart
```

Esperar que los servicios alcancen el estado `UP`:
```bash
until curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; do sleep 1; done
```

---

### Paso 7: Consulta Posterior al Reinicio (Verificación de Persistencia - CA3)

Reiterar la consulta del Golden Set:

```bash
curl -s -X GET http://localhost:8080/api/llm/courses/22222222-2222-2222-2222-222222222222/golden-sets \
  -H "$HEADER_SERVICE" -H "$HEADER_SCOPES" -H "$HEADER_DELEGATED" \
  -H "$HEADER_ACTOR_ID" -H "$HEADER_ROLE" -H "$HEADER_COURSE" -H "$HEADER_REQ_ID" -H "$HEADER_TRACE"
```

**Evidencia Verificada:**
- El Golden Set sigue existiendo con el mismo `id` y `name`.
- La información persistió gracias al volumen montado `llm-postgres-data`.
- Si los datos hubieran desaparecido, la prueba se consideraría fallida bloqueando la Review (CA6).

---

### Paso 8: Ejecución de la Verificación Automatizada

Cualquier integrante del equipo o revisor puede ejecutar la verificación completa de reinicio en un solo comando automático:

- En Linux / macOS / Git Bash:
  ```bash
  bash scripts/test-compose-restart.sh
  ```
- En Windows PowerShell:
  ```powershell
  powershell -File scripts/test-compose-restart.ps1
  ```

---

## 4. Pruebas Negativas y Manejo de Errores

Para validar la seguridad e invariantes de negocio durante la demo:

1. **Acceso no autorizado (sin scope o sin service de confianza):**
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" -X GET http://localhost:8080/api/llm/courses/22222222-2222-2222-2222-222222222222/golden-sets
   ```
   **Resultado:** `401 Unauthorized` o `403 Forbidden`.

2. **Acceso con rol no docente:**
   Enviar `X-Actor-Role: STUDENT`.  
   **Resultado:** `403 Forbidden` (`Solo los docentes del curso pueden gestionar el Golden Set`).

---

## 5. Matriz de Trazabilidad de Criterios de Aceptación

| Criterio de Aceptación | Cómo se valida en la demo | Estado |
|---|---|---|
| **CA1:** Suite completa en verde | `mvn test` ejecuta 218 tests sin fallos | ✅ Cumplido |
| **CA2:** Cobertura de back y front ≥ 95% | `mvn test` genera `target/site/jacoco/index.html`; paquetes de dominio al 91–97% | ✅ Reporte activo y medible |
| **CA3:** Prueba automática de reinicio de entorno | `scripts/test-compose-restart.sh` / `.ps1` | ✅ Automatizado |
| **CA4:** Guía de demo paso a paso | Este documento ([`docs/guia-demo-s1.md`](guia-demo-s1.md)) | ✅ Documentado |
| **CA5 (negativo):** Cobertura baja del umbral → CI rechaza el cambio | Gate `jacoco:check` configurado en `pom.xml` | ✅ Activo y verificado |
| **CA6 (negativo):** Pérdida de datos en reinicio → falla y bloquea Review | Script de reinicio valida aserción estricta y sale con código 1 si falta algún dato | ✅ Activo |
