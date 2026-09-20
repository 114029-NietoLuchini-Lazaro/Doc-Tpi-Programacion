package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;

import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.OptimisticLockException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationUnavailableException;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ar.edu.utn.frc.tup.piv.llm.application.service.CourseGoldenSetService.GoldenSetSizeException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(GoldenSetSizeException.class)
  ProblemDetail goldenSetSize(GoldenSetSizeException exception, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, exception.getMessage(), request);
  }
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException exception, HttpServletRequest request) { return problem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request); }
  @ExceptionHandler(OptimisticLockException.class)
  ProblemDetail staleDraft(OptimisticLockException exception, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, exception.getMessage(), request); }
  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail conflict(IllegalStateException exception, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, exception.getMessage(), request); }
  @ExceptionHandler(ModelInvocationUnavailableException.class)
  ProblemDetail modelUnavailable(ModelInvocationUnavailableException exception, HttpServletRequest request) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de modelos no está disponible temporalmente", request);
  }
  @ExceptionHandler(ProviderException.class)
  ProblemDetail providerUnavailable(ProviderException exception, HttpServletRequest request) {
    HttpStatus status = providerStatus(exception.code());
    ProblemDetail problem = problem(status, providerDetail(status), request);
    problem.setProperty("provider_code", exception.code());
    return problem;
  }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail invalidData(DataIntegrityViolationException exception, HttpServletRequest request) {
    String cause = exception.getMostSpecificCause().getMessage();
    log.warn("Data integrity error while handling {}: {}", request.getRequestURI(), cause);
    String detail = cause != null && cause.contains("golden_set_cases_reference_scores_check")
        ? "No se pudo copiar la versión porque uno de sus casos tiene puntajes de referencia incompatibles. Editá o recreá el caso y volvé a publicar."
        : cause != null && cause.contains("model_deployments_adapter_id_model_id_model_version_key")
            ? "Ese modelo ya existe. Se reutilizará al asignarlo a una tarjeta; volvé a intentar."
            : "Los datos no cumplen el formato requerido; revisá los puntajes y campos obligatorios.";
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, detail, request);
  }
  private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("requestId", request.getHeader("X-Request-Id"));
    return problem;
  }

  private HttpStatus providerStatus(String code) {
    if (code == null) return HttpStatus.SERVICE_UNAVAILABLE;
    return switch (code) {
      case "INVALID_PROVIDER", "PROVIDER_NOT_INSTALLED", "INVALID_CONFIGURATION", "INVALID_CREDENTIAL",
          "PROVIDER_AUTHENTICATION_FAILED", "PROVIDER_INVALID_REQUEST", "PROVIDER_MODEL_NOT_FOUND" ->
          HttpStatus.UNPROCESSABLE_ENTITY;
      default -> code.startsWith("PROVIDER_HTTP_4")
          ? HttpStatus.UNPROCESSABLE_ENTITY
          : HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  private String providerDetail(HttpStatus status) {
    return status == HttpStatus.UNPROCESSABLE_ENTITY
        ? "La configuración o solicitud del proveedor no es válida"
        : "El proveedor de modelos no está disponible temporalmente";
  }
}
