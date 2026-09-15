# Resumen de la conversación y plan para usar MockServer

## Objetivo general

Probar el `llm-service` de forma realista sin depender del API Gateway corporativo ni del microservicio de Cursos productivo.

La comunicación externa se simula, pero el procesamiento interno del `llm-service` continúa siendo real: autorización, calibración, llamadas al AI Gateway, cálculo de MAE, persistencia, trazabilidad y manejo de errores.

## Resumen de los temas tratados

### Calibración del LLM

La calibración no devuelve MAE ficticios. El backend:

1. Recorre los casos del golden set.
2. Envía cada caso al proveedor LLM mediante el AI Gateway.
3. Interpreta la respuesta del modelo.
4. Obtiene los puntajes generados.
5. Los compara con los puntajes esperados.
6. Calcula MAE, máximo error individual y aprobación.
7. Persiste las ejecuciones y sus métricas.

Se detectó que el objetivo de calibración podía resolverse usando siempre el proveedor/modelo activo, aunque el usuario hubiera seleccionado otro candidato en el Workbench. El flujo fue ajustado para seleccionar explícitamente el candidato para calibración.

### Variabilidad entre calibraciones

El mismo modelo, golden set y rúbrica pueden producir resultados diferentes porque los LLM son probabilísticos.

Para reducir la variabilidad se incorporó una política de inferencia reutilizable que contempla:

- Temperatura baja o cero cuando el proveedor lo permite.
- `top_p` controlado.
- `seed` cuando el proveedor lo soporta.
- Formato JSON estructurado cuando está disponible.
- Tres ejecuciones por calibración.
- Persistencia de la configuración utilizada.
- Huella o fingerprint de la política aplicada.
- Grupo estable únicamente cuando todas las ejecuciones cumplen los criterios definidos.
- Conservación del histórico anterior.

El objetivo es limitar el desvío entre ejecuciones, con un margen esperado cercano a MAE ≤ 1. Esto reduce la variabilidad, pero no convierte en matemáticamente determinista a un proveedor que no garantice esa propiedad.

### `seed`

El `seed` es una semilla que algunos proveedores utilizan para inicializar su muestreo. No hace que el modelo razone mejor ni garantiza igualdad absoluta entre proveedores.

El valor `42` es una convención reproducible, no un valor especial. Si el proveedor no soporta `seed`, se envían igualmente los hiperparámetros compatibles, como temperatura, `top_p` y formato de respuesta.

### Workbench

El Workbench fue alineado para permitir:

- Seleccionar un modelo candidato.
- Marcarlo explícitamente para calibración.
- Ver ejecuciones y grupos de estabilidad.
- Activar solamente una calibración estable.
- Diferenciar el modelo activo del modelo seleccionado para calibrar.

### Golden sets y rúbricas

Se solicitó agregar datos iniciales de prueba al levantar la aplicación:

1. Golden set con tres alumnos excelentes, uno regular y uno malo.
2. Golden set con dos excelentes, dos regulares y uno malo.
3. Golden set con un excelente, tres regulares y uno malo.

También se solicitaron tres rúbricas precargadas:

- Exigente.
- Normal.
- Permisiva.

Estos datos deben revisarse antes de integrarlos definitivamente porque el árbol de trabajo contiene cambios concurrentes y reorganización de paquetes.

### Perfil profesor/admin y cursos asignados

El Workbench dejó de depender únicamente de un catálogo local y comenzó a consultar cursos asignados por HTTP.

La autorización espera una membresía con:

- Rol `TEACHER` o `DOCENTE`.
- Estado `ACTIVE` o `ACTIVO`.

Cuando el servicio de Cursos real no está levantado, el profesor no puede acceder correctamente al Workbench. Por eso se necesita un servicio simulado para desarrollo y demos.

### Situación del repositorio

Existe un commit previo con los cambios principales de calibración y Workbench, pero el árbol de trabajo tiene modificaciones locales adicionales relacionadas con:

- Proxy del Workbench.
- Resolución de cursos.
- Autorización.
- Migraciones.
- Seed de datos.
- Reorganización de paquetes.
- Configuración de servicios.

Antes de integrar el gateway simulado se debe separar qué cambios pertenecen al trabajo actual y cuáles son modificaciones concurrentes.

## Arquitectura propuesta con MockServer

```text
Angular Workbench
        │
        ▼
demo-gateway — Nginx
        │
        ├── /api/llm/**     → llm-service
        │
        └── /api/courses/** → demo-courses — MockServer

llm-service
        │
        ▼
demo-gateway
        │
        ▼
demo-courses — MockServer
```

El frontend y el backend utilizan el gateway simulado como frontera de comunicación. MockServer únicamente simula las respuestas del microservicio de Cursos.

## Componentes

### `demo-gateway`

Se propone Nginx porque es liviano, estable y no requiere agregar dependencias Java.

Responsabilidades:

- Enrutar `/api/llm/**` hacia `llm-service`.
- Enrutar `/api/courses/**` hacia MockServer.
- Inyectar headers de identidad del usuario demo.
- Propagar `X-Request-Id` y `traceparent`.
- Eliminar headers de identidad enviados por el navegador y reemplazarlos por valores controlados por el gateway.

El navegador no debe poder elegir libremente su identidad modificando headers. Los valores de profesor demo deben ser configurados solamente en el gateway local.

### `demo-courses`

Se propone usar la imagen Docker de MockServer y definir expectativas para los endpoints necesarios.

Endpoint de cursos asignados:

```http
GET /api/courses/me/course-cohorts
```

Respuesta de ejemplo:

```json
[
  {
    "id": "curso-cohorte-demo-001",
    "courseId": "programacion-iii",
    "name": "Programación III",
    "status": "ACTIVE"
  }
]
```

Endpoint de membresía:

```http
GET /api/courses/curso-cohorte-demo-001/members/11111111-1111-1111-1111-111111111111
```

Respuesta de ejemplo:

```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "role": "TEACHER",
  "status": "ACTIVE"
}
```

MockServer también permitirá probar respuestas `403`, `404`, `500`, latencia artificial y respuestas inválidas.

### `llm-service`

El backend continúa siendo real:

- Controladores.
- Autorización.
- Repositorios.
- Calibración.
- Cálculo y persistencia de MAE.
- AI Gateway.
- Trazabilidad.
- Cliente HTTP de membresías.

Únicamente se reemplaza el servicio externo de Cursos por MockServer.

La URL debe configurarse mediante una variable como:

```yaml
LLM_COURSES_BASE_URL=http://demo-gateway:8080
```

Dentro de Docker no se debe usar `localhost` para acceder a otro contenedor.

### Workbench Angular

El proxy local del Workbench debe apuntar a Nginx:

```text
/api/llm/**     → demo-gateway
/api/courses/** → demo-gateway
```

De esta forma se prueba el flujo real:

```text
Angular → demo-gateway → llm-service
```

## Estructura de archivos prevista

```text
demo/
├── docker-compose.yml
├── gateway/
│   └── nginx.conf
└── courses/
    └── expectations.json
```

El compose incluirá el gateway, MockServer, `llm-service`, PostgreSQL y las dependencias existentes del laboratorio.

## Flujos que deben probarse

### Flujo exitoso

1. Levantar Docker Compose.
2. Abrir el Workbench.
3. Solicitar `/api/courses/me/course-cohorts`.
4. Recibir el curso demo desde MockServer.
5. Seleccionar un modelo.
6. Ejecutar una calibración.
7. Validar la membresía del profesor.
8. Ejecutar las calibraciones reales.
9. Calcular y persistir los MAE.
10. Mostrar el grupo estable o inestable en el Workbench.

### Autorización denegada

Responder desde MockServer con rol `STUDENT` o estado inactivo. El backend debe responder `403` y el Workbench debe mostrar un error controlado.

### Curso inexistente

Responder `404`. El backend no debe aprobar la operación ni producir errores internos no controlados.

### Servicio de Cursos caído

Responder `500`. Se debe comprobar el manejo de Resilience4j, los timeouts, el mensaje RFC 7807 y la preservación de trazabilidad.

### Timeout

Agregar una demora artificial en MockServer y comprobar que no se dupliquen operaciones ni se congele la interfaz.

## Orden de implementación

1. Revisar y separar los cambios locales existentes.
2. Definir el usuario, curso y membresía demo.
3. Crear el contenedor MockServer.
4. Crear las expectativas de Cursos.
5. Crear el contenedor Nginx.
6. Configurar las rutas y headers.
7. Cambiar el Workbench para apuntar al gateway.
8. Configurar `LLM_COURSES_BASE_URL` en el backend.
9. Levantar todo con Docker Compose.
10. Probar respuestas 2xx, 403, 404, 500 y timeout.
11. Ejecutar compilación y pruebas de backend y frontend.
12. Documentar cómo iniciar el entorno y cambiar escenarios.

## Ventajas y límites

Ventajas:

- No se desarrolla un microservicio falso en Java.
- Las respuestas se cambian sin recompilar.
- Se pueden simular errores y latencia.
- Se conservan fronteras HTTP reales.
- El procesamiento del `llm-service` continúa siendo funcional.

Límites:

- MockServer no reemplaza las pruebas contra el API Gateway real.
- La identidad demo no representa autenticación productiva completa.
- Las respuestas de Cursos son simuladas y deben mantenerse alineadas con el contrato real.
- La determinismo de los proveedores LLM sigue dependiendo de sus capacidades.

## Alternativas consideradas

### Nginx solamente

Es la opción más simple: puede enrutar y devolver JSON estático. Sirve para un smoke test básico, pero representa peor la separación entre gateway y microservicio.

### Spring Cloud Gateway

Es más parecido a un gateway productivo y permite filtros para agregar headers. Sin embargo, requiere más código Java, configuración y nuevas dependencias. Para la demo local, Nginx + MockServer ofrece menor complejidad y suficiente realismo.

## Referencias

- [Nginx reverse proxy](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/)
- [MockServer en Docker](https://www.mock-server.com/where/docker.html)
- [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
