# Guía para desarrolladores junior

## Contrato entre Backoffice, `llm-service` y los proveedores de IA

Esta guía describe el contrato vigente entre Backoffice y `llm-service`, y cómo el servicio se conecta con proveedores de IA sin exponer esa complejidad al resto de la plataforma.

La regla principal es:

```text
Backoffice conoce el contrato HTTP de llm-service.
llm-service conoce el contrato particular de cada proveedor.
```

Backoffice nunca construye requests directos para OpenAI, Groq, Anthropic o Gemini y nunca recibe una API key una vez creada.

## 1. Qué resuelve `llm-service`

El servicio centraliza tutor de IA, evaluación de conversaciones, golden sets, rúbricas, calibración, administración de credenciales/modelos y auditoría.

```text
Backoffice → HTTP /api/llm/** → llm-service → SPI de proveedores → proveedor de IA
```

## 2. Arquitectura modular actual

El backend no tiene un enum ni un `switch` central para decidir cómo hablar con cada proveedor. Usa el patrón **Strategy** mediante `AiProviderAdapter`.

| Pieza | Responsabilidad |
|---|---|
| `provider-spi` | Contrato neutral: credenciales, modelos, capacidades, invocación y respuesta. |
| `provider-openai-compatible` | Adaptador para OpenAI, Groq y servicios con contrato OpenAI-compatible. |
| `provider-anthropic` | Adaptador nativo de Anthropic. |
| `provider-gemini` | Adaptador nativo de Google Gemini. |
| `ProviderRegistry` | Registro/factoría: localiza la estrategia por `providerKey` y rechaza claves duplicadas al iniciar. |
| `ProviderInvocationGateway` | Fachada: descifra secretos solo durante la llamada y delega en el adaptador seleccionado. |

Un proveedor nuevo se agrega como módulo/JAR que implemente el SPI. No se modifican controllers, calibración, golden sets ni Backoffice para agregar lógica específica de un proveedor.

### 2.1 La interfaz que implementa cada proveedor

La interfaz central se llama [`AiProviderAdapter`](../../provider-spi/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/spi/AiProviderAdapter.java). Es un contrato: cada proveedor debe implementar las mismas operaciones, aunque su API, SDK y autenticación sean distintos.

```java
public interface AiProviderAdapter {
  ProviderDescriptor descriptor();
  void validate(ProviderCredentialMaterial credential);
  List<ModelDescriptor> discoverModels(ProviderCredentialMaterial credential);
  ProviderReply invoke(ProviderCredentialMaterial credential,
                       ProviderInvocation invocation);
}
```

| Método | Qué resuelve | Ejemplo con Gemini |
|---|---|---|
| `descriptor()` | Describe el proveedor para el resto del sistema: clave, nombre visible, campos de credencial y capacidades. | Declara la clave `gemini` y que necesita una `apiKey`. |
| `validate(...)` | Rechaza una configuración incompleta antes de guardar o invocar. | Comprueba que `apiKey` no sea nula ni vacía. |
| `discoverModels(...)` | Consulta al proveedor y devuelve una lista neutral de `ModelDescriptor`. | Llama a la API de Google y normaliza nombres como `gemini-2.5-flash`. |
| `invoke(...)` | Ejecuta una consulta al modelo y devuelve una respuesta neutral. | Construye la llamada de Gemini con API key, modelo, prompt y timeout. |
| `stream(...)` | Emite partes de la respuesta cuando el proveedor lo permite. La interfaz trae una versión por defecto que entrega el texto completo. | Un adaptador puede sobrescribirlo para usar streaming nativo. |

Los datos que viajan por la interfaz también son neutrales:

| Tipo | Contenido |
|---|---|
| [`ProviderDescriptor`](../../provider-spi/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/spi/ProviderDescriptor.java) | Metadatos y campos que necesita el proveedor. |
| [`ProviderCredentialMaterial`](../../provider-spi/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/spi/ProviderCredentialMaterial.java) | Configuración pública más secretos ya descifrados, disponible solo durante la operación. |
| [`ProviderInvocation`](../../provider-spi/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/spi/ProviderInvocation.java) | Modelo, prompt, parámetros de inferencia y timeout. |
| [`ProviderReply`](../../provider-spi/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/spi/ProviderReply.java) | Texto de respuesta, uso de tokens y fingerprint opcional. |

El recorrido de una llamada es el siguiente:

```text
Credencial con providerKey = "gemini"
  → ProviderRegistry busca el adaptador Gemini
  → ProviderInvocationGateway descifra la API key en memoria
  → GeminiProviderAdapter valida y llama a Google
  → ProviderReply vuelve en un formato común al resto del sistema
```

Para ver implementaciones reales, empezá por [`GeminiProviderAdapter`](../../provider-gemini/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/gemini/GeminiProviderAdapter.java), y luego comparalo con [`AnthropicProviderAdapter`](../../provider-anthropic/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/anthropic/AnthropicProviderAdapter.java) y [`OpenAiCompatibleProviderAdapter`](../../provider-openai-compatible/src/main/java/ar/edu/utn/frc/tup/piv/llm/provider/openai/OpenAiCompatibleProviderAdapter.java). El código común que los selecciona está en [`ProviderRegistry`](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/adapter/out/ai/ProviderRegistry.java) y [`ProviderInvocationGateway`](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/adapter/out/ai/ProviderInvocationGateway.java).

## 3. Catálogo de proveedores disponibles

Backoffice no debe hardcodear proveedores ni campos de formularios. Debe consultar el catálogo instalado:

```http
GET /api/llm/admin/providers
Authorization: Bearer <token>
```

Ejemplo:

```json
{
  "items": [{
    "key": "openai-compatible",
    "displayName": "OpenAI compatible",
    "adapterVersion": "1",
    "credentialFields": [
      { "key": "baseUrl", "label": "URL base", "secret": false, "required": true },
      { "key": "apiKey", "label": "API key", "secret": true, "required": true }
    ],
    "capabilities": { "seed": true, "temperature": true, "topP": true }
  }]
}
```

La lista exacta depende de los módulos instalados. Actualmente se incluyen `openai-compatible`, `anthropic` y `gemini`. Groq se configura como `openai-compatible` con su `baseUrl`; no existe un `providerKey` llamado `groq`.

## 4. Registrar una credencial

Primero se consulta el catálogo para conocer los campos requeridos. Luego se crea la credencial:

```http
POST /api/llm/admin/provider-credentials
Authorization: Bearer <token>
Content-Type: application/json
X-Request-Id: <uuid>
```

Ejemplo para Groq:

```json
{
  "providerKey": "openai-compatible",
  "displayName": "Groq principal",
  "configuration": { "baseUrl": "https://api.groq.com" },
  "secrets": { "apiKey": "<clave>" }
}
```

Ejemplo para Anthropic:

```json
{
  "providerKey": "anthropic",
  "displayName": "Anthropic principal",
  "configuration": {},
  "secrets": { "apiKey": "<clave>" }
}
```

`configuration` contiene valores públicos; `secrets` valores confidenciales. El backend los valida contra el adaptador, cifra los secretos y nunca los devuelve.

```json
{
  "id": "9c3b6f34-3b5c-4c7c-9fd8-7e8c4a0f8d20",
  "providerKey": "openai-compatible",
  "displayName": "Groq principal",
  "configuration": { "baseUrl": "https://api.groq.com" },
  "mask": "•••• configurada",
  "state": "ACTIVE"
}
```

## 5. Descubrir modelos y crear un candidato

```http
POST /api/llm/admin/provider-credentials/{credentialId}/discover-models
```

La respuesta contiene `ModelDescriptor`. Backoffice usa `modelId` y `displayName`; no interpreta el JSON nativo del proveedor.

```http
POST /api/llm/admin/provider-credentials/{credentialId}/deployments
Content-Type: application/json

{ "modelId": "<id-devuelto-por-discover-models-y-validado-con-test-model>", "slot": 1 }
```

No hardcodear un modelo Groq en el backoffice ni en el servicio: el `modelId` debe salir del
descubrimiento del provider o de una selección explícita del admin y debe validarse con
`/test-model` antes de asignarlo. Si Groq retira un modelo, el deployment quedará apuntando a un
`modelId` no disponible y la invocación deberá fallar de forma controlada.

Un deployment identifica el modelo que se prueba; una credencial identifica la cuenta del proveedor. Hay tres slots de candidatos, del 1 al 3.

## 6. Probar, seleccionar y activar un modelo

Prueba rápida antes de crear el deployment:

```http
POST /api/llm/admin/provider-credentials/{credentialId}/test-model
```

Chat de un deployment:

```http
POST /api/llm/admin/evaluator-models/{deploymentId}/chat
Content-Type: application/json

{ "message": "Respondé únicamente: conexión operativa." }
```

El stream SSE está disponible en:

```http
POST /api/llm/admin/evaluator-models/{deploymentId}/chat/stream
```

Después del chat se selecciona el candidato para calibrar:

```http
POST /api/llm/admin/evaluator-models/{deploymentId}/select-for-calibration
```

Solo tras una calibración válida se activa para producción:

```http
POST /api/llm/admin/evaluator-models/{deploymentId}/activate
```

El candidato de calibración y el modelo activo son distintos. Seleccionarlo para calibrar no lo activa; activarlo exige que haya respondido correctamente en el chat administrativo.

## 7. Iniciar una calibración

```http
POST /api/llm/courses/{courseId}/calibrations
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json
```

```json
{
  "rubricVersionId": "<uuid>",
  "goldenSetVersionId": "<uuid>"
}
```

Importante: el request **no incluye `modelDeploymentId`**. `llm-service` toma el candidato seleccionado mediante `select-for-calibration`. Esto evita calibrar un modelo diferente por un payload atrasado o manipulado.

El endpoint devuelve `202 Accepted` y procesa la corrida de forma asíncrona. Backoffice debe consultar su estado, métricas y diagnóstico si falla.

## 8. Cómo se obtienen MAE y evidencia reproducible

`RealCalibrationExecutor` procesa cada caso del golden set:

1. Construye el prompt con rúbrica, conversación y contexto.
2. Invoca el modelo del candidato seleccionado mediante el gateway.
3. Exige cinco puntajes enteros entre 0 y 100: `AUTONOMY`, `CLARITY`, `PROGRESSION`, `COMPLIANCE` y `EFFICIENCY`.
4. Compara contra la referencia humana, calcula MAE/error máximo y persiste resultados por caso.
5. Registra la política efectiva y el fingerprint del proveedor si existe.

No hay resultados simulados: si el modelo devuelve JSON inválido, un error de proveedor o una respuesta incompleta, la corrida falla con diagnóstico. No se aprueba artificialmente.

## 9. Determinismo dentro de lo posible

Backoffice no envía `temperature`, `topP`, `topK`, `seed` ni formato de respuesta. La política institucional `CalibrationInferencePolicy` consulta las capacidades del adaptador.

Para un proveedor compatible intenta usar temperatura `0`, `topP` `1`, un `seed` reproducible, JSON estructurado y hasta 128 tokens. Si un proveedor no soporta `seed`, se envían los parámetros que sí admite y se registra la política efectiva.

Esto reduce variación bajo control del servicio, pero no garantiza MAE idéntico: el proveedor puede cambiar modelo, infraestructura o comportamiento aun usando el mismo seed.

## 10. Responsabilidades de Backoffice

Backoffice debe:

- Consultar `/api/llm/admin/providers` para formularios y catálogo.
- Guardar IDs de credencial y deployment; nunca secretos.
- Seleccionar el candidato antes de pedir calibración.
- Usar `Idempotency-Key` para crear calibraciones.
- Mostrar la calibración como trabajo asíncrono.
- Enviar autenticación, `X-Request-Id` y `traceparent` cuando estén disponibles.
- Manejar `400`, `403`, `404`, `409`, `422`, `429` y `5xx` sin mostrar secretos ni stack traces.

Backoffice no debe llamar proveedores directamente, hardcodear proveedores/campos/capacidades, reenviar API keys ni elegir un deployment en el request de calibración.

## 11. Archivos importantes

- [ProviderCredentialController.java](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/adapter/in/web/ProviderCredentialController.java): contrato administrativo de proveedores, credenciales, modelos y chat.
- [CalibrationRunController.java](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/adapter/in/web/CalibrationRunController.java): contrato de calibración por curso.
- [ProviderRegistry.java](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/adapter/out/ai/ProviderRegistry.java): registro de estrategias.
- [ProviderInvocationGateway.java](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/adapter/out/ai/ProviderInvocationGateway.java): fachada de invocación y descifrado temporal.
- [CalibrationInferencePolicy.java](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/application/CalibrationInferencePolicy.java): política por capacidades.
- [RealCalibrationExecutor.java](../../app/src/main/java/ar/edu/utn/frc/tup/piv/llm/application/service/RealCalibrationExecutor.java): ejecución y MAE reales.
- [Arquitectura de proveedores](../provider-spi/README.md): receta técnica para sumar un proveedor.

## 12. Migración V26

`V26__provider_spi_modular_architecture.sql` recrea de forma intencional datos dependientes de proveedores: credenciales, deployments, asignaciones modelo-función, uso y evidencia de calibración. Conserva cursos, rúbricas y golden sets.

Después del despliegue se deben cargar nuevamente credenciales, descubrir modelos, crear candidatos y ejecutar calibraciones nuevas. La ausencia de MAE histórico no significa una calibración ficticia: la evidencia anterior se recrea bajo el nuevo contrato modular.

## Resumen

Backoffice administra la intención: credenciales, candidato y cuándo calibrar. `llm-service` administra la ejecución: cifra secretos, selecciona la estrategia, normaliza al proveedor, aplica la política de inferencia y registra el MAE real. Esta separación permite reemplazar o sumar proveedores sin propagar cambios específicos al frontend, la calibración ni el dominio.
