package ar.edu.utn.frc.tup.piv.llm.domain.ai;

/** Configuración de modelo asociado a una función de IA. */
public record FunctionModelConfig(
    String provider,
    String modelId,
    String modelVersion,
    boolean enabled) {}
