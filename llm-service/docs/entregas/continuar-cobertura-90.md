# Continuar: subir la cobertura de llm-service a 90 %

> Punto de retoma. Estado al 2026-09-18, commit `0048f3c2` en la rama `facu`.

## Contexto

Se sube la cobertura de `llm-service` a 90 % con tests. Hoy está en ≈85 %. El gate de JaCoCo en
`llm-service/pom.xml` (regla `BUNDLE`) está en `0.55` como piso temporal; hay que subirlo a `0.90`
al llegar.

## Ya hecho

- Tests de integración con Testcontainers (Postgres + pgvector, app completa) en
  `src/test/java/.../it/`: `AbstractIntegrationIT`, `GoldenSetFlowIT`, `RubricFlowIT`,
  `CredentialFlowIT`, `CalibrationFlowIT`, `ImportAndAdminIT`, `TutorRagIT`, `ProviderChatIT`.
- Tests de `ProviderLlmGateway` y `EncryptedSecretService`.
- Bugs de producción corregidos: constructores sin `@Autowired` (la app no arrancaba), enum
  `import_state`, orden de guardado del RAG (FK), operador `<=>` de pgvector.

## Pendiente

1. Verificar y sumar `src/test/.../configuration/JwksAndCorsTest.java` (no está commiteado: falla
   un caso y otro tardó ~3 min, probable timeout de conexión).
2. Seguir cubriendo lo más flojo. Ver cobertura con `mvn -B -q test -Djacoco.skip=true` y leer
   `target/site/jacoco/jacoco.csv`:
   - `ProviderCredentialController`, `ProviderLlmGateway` (ramas Anthropic/Gemini)
   - Moderación: `ModerationRetentionPolicyService`, `ModerationResolution`, `ModerationAppeal`,
     `ModerationResolutionEntity`/`Repository`, `ModerationRetentionPolicy`
   - `CourseGoldenSetRepository`, `CalibrationRunRepository`,
     `ChallengeCalibrationAssignmentRepository`, `JdbcCalibrationWorkflowStore`
   - `RagIngestionService`, `RagChatService`, `RagQueryService`, `EligibleInteractionsService`
   - `GatewayIdentityFilter`, `InstitutionalCalibrationController`
3. Al llegar a 90 %: subir el gate `BUNDLE` del pom a `0.90`, correr `mvn -B verify`, actualizar
   `docs/estado-implementacion/ep-01/README.md` (H06) y commitear.

## Notas

- Docker debe estar corriendo (Testcontainers 1.21.4 ya configurado en el pom).
- `ModerationCourseAuthorization` aún lee el `sub` de un Bearer sin firma como fallback.
