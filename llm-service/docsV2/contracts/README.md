# 05 — Contratos de integración

Esta carpeta es la única entrada para la comunicación entre `llm-service` y otros microservicios.
Usala antes de exponer un endpoint, consumir datos, publicar un evento o asumir una responsabilidad
de otra contraparte.

## Qué vas a encontrar

1. [Mapa de integración](00-mapa-de-integracion.md): actores, canales, responsables y acuerdos
   pendientes.
2. [Requisitos a otros micros](requisitos-a-otros-micros.md): datos, eventos y consultas que
   Tema 07 necesita recibir para responder, evaluar o cerrar un intento.
3. [OpenAPI actual](llm-service.openapi.yaml): contrato HTTP que Tema 07 expone.
4. [AsyncAPI actual](llm-service.asyncapi.yaml): eventos Kafka que Tema 07 publica.
5. [Contratos por equipo](equipos/README.md): explicación completa por contraparte, incluyendo
   responsabilidades, secuencias, errores y pendientes.
6. [Contratos históricos V1](historicos-y-contratos-v1/README.md),
   [mapa histórico](90-mapa-de-integracion-historico.md) y
   [contratos históricos inter-equipos](91-contratos-inter-equipos-historicos.md): antecedentes
   útiles para entender cambios, no fuentes para crear una integración nueva.
7. [Simulador y Mock](MOCK.md): comando de una línea (Prism) y simulación local con Docker Workbench para habilitar integración desacoplada (CA2).

## Cómo leerla

Partí del mapa. Luego elegí la contraparte y leé sus requisitos o su contrato narrativo. Por último
abrí el schema ejecutable que corresponda: **OpenAPI para HTTP** y **AsyncAPI para Kafka**. No copies
JSON desde una explicación narrativa; el YAML es la definición técnica única de campos y tipos.

Cada contrato declara estado: **Propuesto** requiere revisión de pares; **Acordado** tiene aceptación
explícita de productor y consumidor; **Implementado** además cuenta con evidencia en código y
pruebas. No infieras un estado por el solo hecho de encontrar un endpoint o evento documentado.

## Cómo actualizar un contrato

Un cambio de contrato modifica en el mismo trabajo la vista por equipo, el schema afectado y la
evidencia/prueba que lo valida. Si cambia una regla, registralo en [`registro/`](../registro/README.md).
Los archivos `11-equipos/*/contratos.md` son punteros de navegación, no una segunda fuente editable.
