# Plan ejecutable de reestructuración de `llm-service`

> **Estado inicial:** pendiente de ejecución  
> **Tipo:** plan de implementación y checklist operativo  
> **Audiencia:** agentes o desarrolladores responsables de ejecutar el refactor  
> **Objetivo:** eliminar la estructura Maven híbrida, normalizar el scaffolding y establecer límites hexagonales verificables sin cambiar el comportamiento externo.

## 1. Resultado esperado

El repositorio debe quedar como un reactor Maven multi-módulo estándar. Cada módulo será dueño de sus fuentes y recursos; no habrá rutas `../src` ni un `src` compartido en la raíz.

La aplicación conservará:

- Una única API Spring Boot ejecutable.
- Los módulos independientes `provider-spi`, `provider-gemini`, `provider-anthropic` y `provider-openai-compatible`.
- Los endpoints, payloads, headers, códigos HTTP y streams SSE actuales.
- Los contratos Kafka y el esquema PostgreSQL actuales.
- Las coordenadas Maven y el nombre actual del JAR.

Este refactor no autoriza cambios funcionales, nuevas migraciones Flyway, cambios de contratos HTTP/Kafka ni modificaciones de datos.

## 2. Situación inicial comprobada

El POM raíz declara cinco módulos:

```text
provider-spi
provider-openai-compatible
provider-anthropic
provider-gemini
app
```

Los módulos de proveedores tienen un layout Maven normal. El problema está en `app/pom.xml`, que compila fuentes externas mediante:

```xml
<sourceDirectory>../src/main/java</sourceDirectory>
<testSourceDirectory>../src/test/java</testSourceDirectory>
<resources>
  <resource>
    <directory>../src/main/resources</directory>
  </resource>
</resources>
```

Esto produce una estructura híbrida: `app` es el ejecutable, pero sus archivos pertenecen físicamente al `src` de la raíz.

También existen dependencias de capas incorrectas:

- Controllers que importan repositories y gateways concretos.
- Servicios de aplicación que importan implementaciones JDBC.
- Records de persistencia expuestos hacia aplicación y API.
- `FakeModelAdapter` dentro de fuentes de producción aunque solo se utiliza en tests.
- `RealCaseAnonymizer` dentro de dominio aunque depende de Jackson.

### Baseline de pruebas

La inspección inicial encontró 101 tests. En la máquina actual, 68 terminan en error antes de ejecutarse porque se usa JDK 25 y Mockito/Byte Buddy no puede auto-adjuntar su agente. El proyecto declara Java 21.

El refactor debe verificarse con JDK 21. No se deben eliminar, excluir ni relajar tests para ocultar este problema ambiental.

## 3. Estructura final obligatoria

```text
llm-service/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── mvnw
├── mvnw.cmd
├── pom.xml
├── app/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/ar/edu/utn/frc/tup/piv/llm/
│       │   │   ├── LlmServiceApplication.java
│       │   │   ├── domain/
│       │   │   ├── application/
│       │   │   │   ├── model/
│       │   │   │   ├── port/out/
│       │   │   │   ├── service/
│       │   │   │   └── worker/
│       │   │   ├── adapter/
│       │   │   │   ├── in/web/
│       │   │   │   ├── in/web/dto/
│       │   │   │   ├── in/web/security/
│       │   │   │   ├── in/messaging/
│       │   │   │   ├── out/ai/
│       │   │   │   ├── out/http/
│       │   │   │   ├── out/messaging/
│       │   │   │   └── out/persistence/
│       │   │   └── configuration/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-workbench.yml
│       │       ├── db/migration/
│       │       └── prompts/
│       └── test/
│           ├── java/
│           └── resources/
├── provider-spi/
│   ├── pom.xml
│   └── src/main/java/
├── provider-gemini/
│   ├── pom.xml
│   └── src/{main,test}/
├── provider-anthropic/
│   ├── pom.xml
│   └── src/{main,test}/
├── provider-openai-compatible/
│   ├── pom.xml
│   └── src/{main,test}/
├── llm-workbench/
├── demo/
├── docs/
├── scripts/
├── Dockerfile
├── compose.yaml
└── compose.workbench.yaml
```

Reglas no negociables:

1. El POM raíz tiene `packaging=pom` y no contiene fuentes ni recursos.
2. `app` es el único módulo ejecutable Spring Boot.
3. Cada módulo es dueño exclusivo de su `src`.
4. No debe existir `llm-service/src` al finalizar.
5. Ningún POM puede apuntar a fuentes o recursos externos con `../`.
6. No se deben convertir los proveedores en microservicios separados.
7. `llm-workbench`, `demo`, `docs` y `scripts` permanecen como herramientas o documentación del mismo repositorio, no como módulos Java.

## 4. Dirección de dependencias

La dirección permitida será:

```text
adapter.in ──────► application ──────► domain
adapter.out ─────► application.port.out / application.model / domain
configuration ──► todas las capas, únicamente para composición

provider-gemini ────────────────► provider-spi
provider-anthropic ─────────────► provider-spi
provider-openai-compatible ─────► provider-spi
app ────────────────────────────► provider-spi + módulos de proveedores
```

Dependencias prohibidas:

```text
domain          -X-> Spring, Jakarta, JDBC, Kafka, Jackson, LangChain4j, adapter
application     -X-> adapter, controllers, JDBC, Kafka, SDK de proveedor
adapter.in      -X-> adapter.out
adapter.out     -X-> adapter.in
app             -X-> paquetes concretos internos de Gemini/Anthropic/OpenAI
provider-*      -X-> app
Controller      -X-> Repository/JdbcTemplate/gateway concreto
```

## 5. Procedimiento de ejecución

### Fase 0 — Protección del trabajo existente

Registro de ejecución — 2026-09-15, agente `/root`, branch `main`.

Inventario inicial (`git status --short`): cambios locales en `Dockerfile`,
`pom.xml`, workbench y fuentes de `src`; módulos `provider-*`, `app`, material
de demo y documentación sin versionar. Se preservan todos esos cambios. Los
artefactos y respaldos explícitamente enumerados en la fase 9 se eliminan solo
en esa fase.

- [ ] Registrar fecha, agente y branch actual en este archivo.
- [ ] Copiar debajo de esta fase la salida de `git status --short`.
- [ ] Registrar los archivos modificados y no versionados relacionados con providers y credenciales.
- [ ] No ejecutar `git reset`, `git checkout`, `git clean` ni borrar cambios existentes.
- [ ] No crear commits salvo autorización explícita.
- [ ] Confirmar que los módulos nuevos y los archivos no versionados serán incluidos en el movimiento.

**Gate 0:** existe un inventario que permite demostrar que ningún cambio previo se perdió.

### Fase 1 — Normalización física de Maven

- [ ] Crear `app/src/main` y `app/src/test` con el layout Maven estándar.
- [ ] Mover todo `src/main/java` a `app/src/main/java`.
- [ ] Mover todo `src/main/resources` a `app/src/main/resources`.
- [ ] Mover todo `src/test/java` a `app/src/test/java`.
- [ ] Mover `src/test/resources` si existe a `app/src/test/resources`.
- [ ] Incluir en el movimiento archivos modificados y no versionados, especialmente `ProviderRegistry`, `ProviderInvocationGateway`, tests de gateway y `V26__provider_spi_modular_architecture.sql`.
- [ ] Comparar cantidad y contenido de archivos antes de retirar el `src` raíz.
- [ ] Eliminar de `app/pom.xml` `sourceDirectory`, `testSourceDirectory` y el bloque `resources` con rutas `../src`.
- [ ] Mantener `artifactId=llm-service`, la versión actual y el plugin de Spring Boot.
- [ ] Mantener los cinco módulos y su orden en el reactor raíz.
- [ ] Confirmar que `LlmServiceApplication` permanece en `ar.edu.utn.frc.tup.piv.llm` para conservar el component scan.
- [ ] Eliminar el directorio raíz `src` solamente después de comprobar que quedó vacío.

**Gate 1:** `mvn -q -pl app -am -DskipTests package` genera `app/target/llm-service-0.0.1-SNAPSHOT.jar` sin rutas personalizadas.

### Fase 2 — Maven Wrapper y reproducibilidad

- [ ] Incorporar Maven Wrapper 3.9.15: `mvnw`, `mvnw.cmd` y `.mvn/wrapper/maven-wrapper.properties`.
- [ ] Configurar release Java 21 en el POM padre.
- [ ] Actualizar README, AGENTS y scripts para usar `./mvnw` desde la raíz de `llm-service`.
- [ ] No agregar workarounds para ejecutar Mockito con JDK 25.
- [ ] Documentar JDK 21 como precondición local y de CI.

**Gate 2:** `./mvnw -version` informa Maven 3.9.15 y Java 21.

### Fase 3 — Reorganización de paquetes

Realizar primero movimientos de paquetes sin cambiar comportamiento.

- [ ] Mover `api` a `adapter.in.web`.
- [ ] Mover las clases de autorización HTTP de `security` a `adapter.in.web.security`.
- [ ] Mover `CallerIdentity` a `application.model`.
- [ ] Mover `infrastructure.persistence` a `adapter.out.persistence`.
- [ ] Mover `infrastructure.ai`, `ProviderRegistry`, `ProviderInvocationGateway` y `EncryptedSecretService` a `adapter.out.ai`.
- [ ] Mover `GatewayCoursesMembershipClient` a `adapter.out.http`.
- [ ] Mover `KafkaAttemptEventsListener` a `adapter.in.messaging`.
- [ ] Mover `KafkaEventPublisher` y `KafkaOutboxDispatcher` a `adapter.out.messaging`.
- [ ] Mover servicios de casos de uso a `application.service`.
- [ ] Mover workers y schedulers a `application.worker`.
- [ ] Mover `RealCaseAnonymizer` desde `domain` a `application.service`, porque utiliza Jackson.
- [ ] Mantener en `domain` únicamente reglas, excepciones y modelos sin dependencias externas.
- [ ] Actualizar paquetes, imports, tests y documentación tras cada grupo de movimientos.

**Gate 3:** la aplicación compila conservando exactamente los contratos externos existentes.

### Fase 4 — Puertos de salida y modelos neutrales

Crear en `application.port.out` estas interfaces:

- [ ] `AuditPort`
- [ ] `CalibrationArtifactPort`
- [ ] `CalibrationRunPort`
- [ ] `ChallengeCalibrationAssignmentPort`
- [ ] `CourseEvaluationStatusPort`
- [ ] `CourseGoldenSetPort`
- [ ] `CourseMembershipPort`
- [ ] `FunctionModelConfigPort`
- [ ] `GoldenSetImportPort`
- [ ] `GoldenSetUpdateProposalPort`
- [ ] `IdempotencyPort`
- [ ] `ModelDeploymentPort`
- [ ] `ModelInvocationPort`
- [ ] `ProcessedEventPort`
- [ ] `ProviderCatalogPort`
- [ ] `ProviderCredentialPort`
- [ ] `ProviderInvocationPort`
- [ ] `RubricVersionPort`
- [ ] `CalibrationWorkflowStore`

Reglas para su creación:

1. Cada puerto conserva la semántica y las operaciones públicas que actualmente utiliza la aplicación.
2. Ningún puerto expone `JdbcTemplate`, `ResultSet`, una clase `Repository` ni un tipo anidado dentro de infraestructura.
3. Los records compartidos se convierten en tipos superiores, uno por archivo, dentro de `application.model`.
4. Se conservan nombres de campos, nulabilidad y semántica actuales.
5. Los adaptadores de persistencia implementan el puerto correspondiente y se nombran `Jdbc<Responsabilidad>Adapter`.
6. `ProviderCatalogPort` expone catálogo, validación y descubrimiento sin filtrar clases concretas de proveedores.
7. `ProviderInvocationPort` abstrae invocación y streaming de modelos.

Mover los puertos ya existentes:

- [ ] `AttemptStartedPort` → `application.port.out`.
- [ ] `ChallengeEnablementPort` → `application.port.out`.
- [ ] `CourseMembershipPort` → `application.port.out`.
- [ ] `ModelInvocationPort` → `application.port.out`.
- [ ] `CalibrationWorkflowStore` → archivo superior en `application.port.out`.
- [ ] `RecalibrationTrigger` → archivo superior en `application.port.out`.

**Gate 4:** ninguna clase de `application` importa `adapter.out` o `infrastructure`.

### Fase 5 — Casos de uso para controllers acoplados

Crear estos servicios de aplicación:

- [ ] `CourseEvaluationStatusService`
- [ ] `InstitutionalCalibrationService`
- [ ] `ModelAssignmentService`
- [ ] `ModelDeploymentService`
- [ ] `ProviderAdministrationService`

Además:

- [ ] Ampliar `CalibrationRunService` para encapsular la selección del deployment de calibración.
- [ ] Reemplazar en todos los controllers cualquier dependencia directa de repository, registro de proveedores, cifrado o gateway.
- [ ] Mantener SSE y construcción HTTP en `adapter.in.web`; delegar selección, validación, cifrado e invocación al servicio de aplicación.
- [ ] Extraer todos los DTOs HTTP anidados como records superiores en `adapter.in.web.dto`.
- [ ] Mantener exactamente los nombres JSON actuales mediante nombres de componentes o anotaciones Jackson solo cuando sean necesarias.
- [ ] Actualizar tests para mockear puertos o servicios de aplicación, nunca implementaciones JDBC desde un controller test.

**Gate 5:** ningún controller importa una clase de `adapter.out`, `Repository`, `JdbcTemplate` o SDK de proveedor.

### Fase 6 — Fakes y dobles de prueba

- [ ] Mover `FakeModelAdapter` a `app/src/test/java/.../adapter/out/ai/`.
- [ ] Mantener sus escenarios de respuesta normal, demora y respuesta inválida.
- [ ] Actualizar `FakeModelAdapterTest` y los tests que lo utilicen.
- [ ] Verificar que ninguna configuración Spring de producción intenta inyectarlo.

**Gate 6:** `FakeModelAdapter.class` no aparece dentro del JAR ejecutable.

### Fase 7 — Gate de arquitectura con ArchUnit

- [ ] Agregar ArchUnit únicamente con scope `test` en `app/pom.xml`.
- [ ] Crear `ApplicationArchitectureTest`.
- [ ] Hacer cumplir todas las dependencias permitidas y prohibidas de la sección 4.
- [ ] Exigir que controllers estén en `adapter.in.web`.
- [ ] Exigir que clases JDBC estén en `adapter.out.persistence`.
- [ ] Exigir que listeners Kafka estén en `adapter.in.messaging` y publishers en `adapter.out.messaging`.
- [ ] Exigir que ningún código de `app` importe paquetes concretos de los proveedores.
- [ ] Exigir que los módulos de proveedores dependan de `provider-spi`, no de `app`.

Crear tests locales, sin red ni claves reales, para cada provider:

- [ ] Verificar `descriptor()`.
- [ ] Verificar campos de credencial requeridos.
- [ ] Verificar rechazo de API key ausente o vacía.
- [ ] No ejecutar descubrimiento real de modelos como prueba unitaria.

**Gate 7:** los tests de ArchUnit fallan deliberadamente al introducir una dependencia prohibida y pasan al restaurar el código correcto.

### Fase 8 — Documentación arquitectónica

- [ ] Crear `docs/38-justificacion-scaffolding-maven-multimodulo.md`.
- [ ] Explicar por qué los módulos Maven no son microservicios ni APIs independientes.
- [ ] Documentar el problema original de `app` consumiendo `../src`.
- [ ] Incluir el árbol final de la sección 3.
- [ ] Documentar dirección de dependencias y responsabilidades por carpeta.
- [ ] Explicar dónde colocar controllers, DTOs, casos de uso, dominio, puertos, JDBC, HTTP, Kafka y adaptadores LLM.
- [ ] Incluir el procedimiento para agregar un proveedor implementando `AiProviderAdapter`.
- [ ] Incluir anti-patrones prohibidos y comandos de validación.
- [ ] Reescribir `docs/37-estructura-carpetas-backend.md` como mapa vigente y enlazar la justificación del documento 38.
- [ ] Actualizar README, AGENTS, `docs/24-convenciones-cobertura.md` y la guía junior con rutas `app/src`.
- [ ] Marcar las rutas anteriores de documentos históricos como una fotografía del estado anterior, sin reescribir su contenido histórico.
- [ ] Buscar referencias activas a `llm-service/src`, `../../src/main`, `../src/main` y `../src/test`.

**Gate 8:** no existen documentos vigentes que describan estructuras contradictorias.

### Fase 9 — Limpieza auditada

Eliminar únicamente estos residuos comprobados:

- [ ] Todos los `.DS_Store` dentro de `llm-service`.
- [ ] Directorios `target` generados mediante `./mvnw clean`.
- [ ] `demo/README.md.bak`.
- [ ] `demo/docker-compose.yml.bak`.
- [ ] `llm-workbench/src/app/teacher/calibrations-page/calibrations-page.spec.ts.bak`.
- [ ] `llm-workbench/src/app/teacher/calibrations-page/calibrations-page.ts.bak`.
- [ ] `llm-workbench/src/app/teacher/rubrics-page/rubrics-page.spec.ts.bak`.
- [ ] `patch_html.py`, porque es un script temporal no referenciado cuyo resultado ya está aplicado.

Conservar explícitamente:

- `.env` local y no versionado.
- `.idea` local y no versionado.
- `demo/courses`.
- `demo/gateway`.
- Todos los cambios funcionales existentes.

Crear `llm-service/.gitignore` autocontenido con:

```gitignore
**/target/
**/.DS_Store
.idea/
*.iml
.env
.env.*
!.env.example
*.bak
llm-workbench/node_modules/
llm-workbench/dist/
llm-workbench/.angular/
```

Actualizar `.dockerignore` con:

```dockerignore
.git
.idea
.env
**/.DS_Store
**/target
**/*.bak
llm-workbench/node_modules
llm-workbench/dist
llm-workbench/.angular
docs
demo
```

**Gate 9:** el árbol visible contiene solo código, configuración, documentación y herramientas vigentes.

## 6. Compatibilidad obligatoria

No modificar durante este plan:

- Endpoints bajo `/api/llm/**`.
- Métodos HTTP, payloads, respuestas, códigos de estado o SSE.
- Headers `traceparent`, `X-Request-Id`, identidad delegada o autenticación.
- Topics o envelopes Kafka.
- Tablas, columnas, enums o datos PostgreSQL.
- Contenido de migraciones Flyway.
- Checksums de migraciones ya aplicadas.
- `groupId`, `artifactId`, versión o nombre final del JAR.
- Contrato público de `AiProviderAdapter`.
- Funcionalidad del workbench.

Mover las migraciones sin editar su contenido garantiza que Flyway conserve los checksums.

## 7. Verificación final obligatoria

Ejecutar desde la raíz de `llm-service` con JDK 21:

```bash
./mvnw -version
./mvnw clean compile
./mvnw -pl provider-spi test
./mvnw -pl provider-gemini -am test
./mvnw -pl provider-anthropic -am test
./mvnw -pl provider-openai-compatible -am test
./mvnw -pl app -am test
./mvnw verify
./mvnw jacoco:report
```

Verificar frontend:

```bash
cd llm-workbench
npm test -- --watch=false
npm run build -- --configuration=production
cd ..
```

Verificar contenedor y escenario real:

```bash
docker compose build llm-service
docker compose up -d --wait
./scripts/smoke-compose.sh
docker compose down --volumes --remove-orphans
```

Comprobaciones adicionales:

- [ ] Cobertura mínima de 95 % conforme a la política del repositorio.
- [ ] `/actuator/health` devuelve `UP`.
- [ ] El catálogo devuelve los providers instalados.
- [ ] El alta de credencial conserva el contrato vigente.
- [ ] El descubrimiento de modelos usa el adaptador correcto.
- [ ] Los errores 400 y 5xx no exponen secretos ni stack traces.
- [ ] El JAR contiene aplicación, recursos y migraciones.
- [ ] El JAR no contiene `FakeModelAdapter`.
- [ ] Las dependencias de providers quedan empaquetadas en el ejecutable Spring Boot.
- [ ] No existe `llm-service/src`.
- [ ] No existen rutas Maven `../src`.
- [ ] No existen imports Controller → persistence/gateway.
- [ ] No existen imports application → adapter.
- [ ] No existen imports app → implementación concreta de proveedor.
- [ ] `git status` demuestra que los cambios anteriores fueron preservados.

Registrar en este documento la salida resumida y el resultado de cada comando.

## 8. Criterios de aceptación

El trabajo se considera terminado solamente cuando:

1. IntelliJ muestra cada módulo con su propio `src`; `app` no utiliza un source root externo.
2. El reactor completo compila desde la raíz con Maven Wrapper y JDK 21.
3. Todos los tests y reglas ArchUnit pasan.
4. El JAR y la imagen Docker arrancan correctamente.
5. No cambió ningún contrato externo ni dato persistido.
6. `domain` y `application` respetan la dirección de dependencias definida.
7. Los controllers no conocen persistencia ni proveedores concretos.
8. Los módulos de proveedores continúan siendo extensibles mediante `provider-spi`.
9. La documentación refleja únicamente la estructura real.
10. No quedan archivos temporales o artefactos de compilación mezclados con las fuentes.

Si falla cualquiera de estos puntos, no se debe marcar el plan como completado.
