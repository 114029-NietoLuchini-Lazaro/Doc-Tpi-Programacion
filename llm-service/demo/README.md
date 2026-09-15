# Demo del Entorno con MockServer y API Gateway

Este directorio contiene todo lo necesario para ejecutar la aplicación de forma local, simulando la infraestructura real de microservicios mediante MockServer y Nginx.

## Arquitectura Simulada

- **Frontend (Angular)**: Levanta localmente en el puerto 4200.
- **Nginx (API Gateway)**: Corre en el puerto `8080`. Recibe las peticiones del frontend, inyecta los headers de identidad obligatorios (`X-Service-Id`, `X-Delegated-User`) y rutea el tráfico.
- **Backend (Spring Boot)**: Es tu aplicación `llm-service`, levantada de forma nativa o en docker.
- **MockServer**: Simula a `courses-service` respondiendo en el puerto `1080` (interno).

## Instrucciones de Inicio

1. **Levantar la Infraestructura:**
   Abre una terminal en este directorio (`demo/`) y ejecuta:
   ```bash
   docker compose up -d
   ```
   Esto iniciará `llm-service`, el proxy Nginx y el MockServer preconfigurado.

2. **Levantar el Frontend (Workbench):**
   Abre otra terminal en la carpeta raíz del proyecto, navega a `llm-workbench/` e inicia la aplicación Angular:
   ```bash
   npm start
   ```
   El frontend utilizará automáticamente el archivo `proxy.local.json` para enviar todo el tráfico de `/api/*` al API Gateway (Nginx) en `http://localhost:8080`.

3. **Abrir la Aplicación:**
   Ingresa a [http://localhost:4200](http://localhost:4200)

## Escenarios de Prueba (UUIDs Mágicos)

Para probar cómo reacciona la interfaz y el backend a distintas respuestas del servicio de cursos (como fallas, permisos insuficientes, o demoras), utilizamos "UUIDs mágicos".

Podes forzar distintos escenarios simplemente navegando al curso correspondiente en el frontend (modificando la URL en la barra de direcciones `http://localhost:4200/courses/<UUID>` o inyectándolo en tu estado de prueba).

| UUID del Curso | Rol Simulado | Escenario a Probar | Código HTTP que devuelve el Mock |
| :--- | :--- | :--- | :--- |
| `22222222-2222-2222-2222-222222222222` | **TEACHER** | Flujo exitoso (Camino feliz) | `200 OK` |
| `33333333-3333-3333-3333-333333333333` | **STUDENT** | Acceso denegado (Solo profes pueden entrar) | `403 Forbidden` |
| `44444444-4444-4444-4444-444444444444` | *N/A* | Curso inexistente | `404 Not Found` |
| `55555555-5555-5555-5555-555555555555` | *N/A* | Falla interna en el servicio de cursos | `500 Internal Server Error` |
| `66666666-6666-6666-6666-666666666666` | *N/A* | Latencia extrema (demora 5 segundos) | `503 Service Unavailable / Timeout` |

> **Nota:** Para cambiar de usuario (si necesitás simular la identidad de otro profesor), podes editar `demo/gateway/nginx.conf` y cambiar el valor estático del header `X-Delegated-User`.
