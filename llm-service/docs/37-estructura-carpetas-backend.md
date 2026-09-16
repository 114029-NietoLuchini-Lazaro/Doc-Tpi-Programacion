# 37 — Mapa vigente del backend

> Estructura vigente desde 2026-09-15. La justificación y el procedimiento para
> agregar proveedores están en [38](38-justificacion-scaffolding-maven-multimodulo.md).

`llm-service` es un único microservicio Spring Boot. `app` es su único módulo
ejecutable; `provider-spi` y `provider-*` son bibliotecas Maven, no servicios.

```text
app/src/main/java/ar/edu/utn/frc/tup/piv/llm/
├── LlmServiceApplication.java
├── domain/                    # reglas y modelos Java puros
├── application/
│   ├── model/                 # tipos neutrales compartidos
│   ├── port/out/              # puertos requeridos por los casos de uso
│   ├── service/               # casos de uso y orquestación
│   └── worker/                # tareas programadas y workers
├── adapter/
│   ├── in/web/                # controllers, advice y DTOs HTTP
│   │   └── security/          # autorización del borde HTTP
│   ├── in/messaging/          # consumidores Kafka
│   └── out/
│       ├── ai/                # gateway LLM, registro y cifrado de secretos
│       ├── http/              # clientes HTTP salientes
│       ├── messaging/         # publicadores y dispatcher Kafka
│       └── persistence/       # adaptadores JDBC
└── configuration/             # composición Spring y configuración
```

Los recursos están en `app/src/main/resources/`; las migraciones Flyway se
conservan sin cambios en `app/src/main/resources/db/migration/`. Las pruebas viven
en `app/src/test/`. No existe un `src/` compartido en la raíz.

## Dirección de dependencias

`adapter.in` delega en `application`; `application` usa `domain` y sus puertos;
`adapter.out` implementa integraciones técnicas. `configuration` compone las
capas. El dominio no importa Spring, JDBC, Kafka, Jackson, LangChain4j ni adapters.

No se permite que un controller use repositorios, `JdbcTemplate`, gateways o SDKs
de proveedores concretos. Un servicio de aplicación debe depender de un puerto y
un modelo neutral, no de un adapter de salida.

## Ubicación de código nuevo

- Endpoint y DTO HTTP: `adapter/in/web` y `adapter/in/web/dto`.
- Caso de uso y puerto: `application/service` y `application/port/out`.
- Regla sin framework: `domain`.
- JDBC, HTTP, Kafka saliente o LLM: el subdirectorio de `adapter/out` correspondiente.
- Listener Kafka: `adapter/in/messaging`; composición de beans: `configuration`.

Validar desde la raíz con `./mvnw clean compile` y `./mvnw -pl app -am test` usando JDK 21.
Los documentos históricos que mencionan `llm-service/src` son fotografías previas.
