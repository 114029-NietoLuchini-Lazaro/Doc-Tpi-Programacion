-- LLM-S03-H01: conecta la calibración con el puerto de invocación de LLM-S01-H10.
-- Semilla de la función 'evaluator' en la tabla función->proveedor+modelo (V13), mismo patrón
-- que la semilla de 'tutor'.

INSERT INTO function_model_config (function, provider, model_id, model_version, enabled)
VALUES ('evaluator', 'fake', 'fake-evaluator-v1', '1', true);
