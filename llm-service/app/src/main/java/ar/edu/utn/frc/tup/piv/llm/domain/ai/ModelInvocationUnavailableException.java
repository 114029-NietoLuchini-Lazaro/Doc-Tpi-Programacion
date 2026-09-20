package ar.edu.utn.frc.tup.piv.llm.domain.ai;

/** Falla operativa esperable al intentar invocar un modelo real: proveedor caído, credencial
 * inválida, modelo no disponible o configuración runtime incompleta. */
public class ModelInvocationUnavailableException extends RuntimeException {
  public ModelInvocationUnavailableException(String message) { super(message); }
  public ModelInvocationUnavailableException(String message, Throwable cause) { super(message, cause); }
}
