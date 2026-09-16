# Arquitectura de proveedores de IA

El núcleo de `llm-service` no conoce SDKs, nombres de proveedores ni formatos de autenticación. Solo
depende de `provider-spi`, que declara la estrategia `AiProviderAdapter`.

## Módulos

- `provider-spi`: contrato estable, DTOs neutrales y capacidades.
- `provider-openai-compatible`: OpenAI, Groq y otros servidores compatibles.
- `provider-anthropic`: integración nativa Anthropic.
- `provider-gemini`: integración nativa Google Gemini.
- `app`: aplicación Spring que registra los módulos que se empaquetan.

`ProviderRegistry` es la factoría/registro: selecciona la estrategia por `providerKey`, rechaza claves
duplicadas al iniciar y expone el catálogo que consume el backoffice. `ProviderInvocationGateway` es la
fachada que descifra una credencial solo durante la llamada y delega al adaptador correcto.

## Agregar un proveedor

1. Crear un módulo Maven que dependa solo de `provider-spi` y de su SDK.
2. Implementar `AiProviderAdapter`: descriptor, validación, descubrimiento de modelos e invocación.
3. Declarar una `@AutoConfiguration` que aporte el adaptador y registrarla en
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
4. Agregar el módulo como dependencia de `app` (o distribuirlo como JAR de extensión en el despliegue).
5. Probar que `GET /api/llm/admin/providers` lo expone y que una credencial puede descubrir modelos.

No se modifica el dominio, los controladores de calibración ni las tablas por proveedor. Cada adaptador
publica qué campos requiere y qué capacidades tiene; la política de calibración decide temperatura,
`topP`, `topK`, `seed` y JSON estructurado a partir de esas capacidades, sin `switch` por proveedor.

## Contrato administrativo

`POST /api/llm/admin/provider-credentials` recibe `providerKey`, `displayName`, `configuration` y
`secrets`. Los secretos se cifran y nunca se devuelven. Los campos válidos se obtienen primero desde
`GET /api/llm/admin/providers`.

La calibración no acepta `modelDeploymentId` desde el cliente: siempre toma el candidato seleccionado
en el servidor. Eso evita que el chat, la calibración y la evidencia MAE se ejecuten contra modelos
distintos por un payload desactualizado.

## Migración V26

V26 es intencionalmente destructiva solo para datos dependientes de proveedores: credenciales,
deployments, asignaciones de funciones, calibraciones y uso. Conserva cursos, rúbricas y golden sets.
Después de desplegar se deben volver a cargar las credenciales, descubrir modelos y recrear candidatos
y calibraciones.
