# Variante empaquetada del laboratorio de integración

Este directorio conserva la variante empaquetada del laboratorio local: Nginx simula el borde del
API Gateway y MockServer simula exclusivamente `courses-service`. No hay mocks de la lógica de
Tema 07: `llm-service`, PostgreSQL y Flyway se ejecutan de verdad.

## Arquitectura Simulada

- **Frontend (llm-workbench)**: Construido y servido en Docker en el puerto 4200.
- **Nginx (Gateway mock)**: Corre en el puerto `8080`. Descarta identidad enviada por el navegador, agrega identidad delegada y correlación, y rutea el tráfico.
- **Backend (Spring Boot)**: Es tu aplicación `llm-service`, levantada en Docker (puerto interno 8080).
- **MockServer**: Simula a `courses-service` respondiendo en el puerto `1080` (interno).

## Instrucciones de Inicio

1. **Levantar Todo el Stack:**
   Abre una terminal en este directorio (`demo/`) y ejecuta:
   ```bash
   docker compose up --build -d
   ```
   *(Esto compilará el backend en Java, compilará el frontend en Angular y levantará todos los servicios conectados)*.

2. **Abrir la Aplicación:**
   Una vez que termine el build y los contenedores estén corriendo, ingresa a [http://localhost:4200](http://localhost:4200)

Para desarrollo diario se prefiere, desde `llm-service/`, el comando
`docker compose -f compose.yaml -f compose.workbench.yaml up --build`: conserva hot reload de
Angular y usa esta misma configuración de Gateway y Cursos.

## Escenarios de Prueba (UUIDs controlados)

Para probar cómo reacciona la interfaz y el backend a distintas respuestas del servicio de cursos (como fallas, permisos insuficientes, o demoras), utilizamos "UUIDs mágicos".

Podes forzar distintos escenarios simplemente navegando al curso correspondiente en el frontend (modificando la URL en la barra de direcciones `http://localhost:4200/courses/<UUID>`).

| UUID del Curso | Rol Simulado | Escenario a Probar | Código HTTP que devuelve el Mock |
| :--- | :--- | :--- | :--- |
| `22222222-2222-2222-2222-222222222222` | **TEACHER** | Flujo exitoso (Camino feliz) | `200 OK` |
| `33333333-3333-3333-3333-333333333333` | **STUDENT** | Acceso denegado (Solo profes pueden entrar) | `403 Forbidden` |
| `44444444-4444-4444-4444-444444444444` | *N/A* | Curso inexistente | `404 Not Found` |
| `55555555-5555-5555-5555-555555555555` | *N/A* | Falla interna en el servicio de cursos | `500 Internal Server Error` |
| `66666666-6666-6666-6666-666666666666` | *N/A* | Latencia extrema (demora 5 segundos) | `503 Service Unavailable / Timeout` |

> **Nota:** la identidad de desarrollo vive en el Gateway mock, no en el workbench ni en
> `llm-service`. Cambiarla debe hacerse como un escenario explícito de gateway, nunca enviando
> headers desde el navegador.
