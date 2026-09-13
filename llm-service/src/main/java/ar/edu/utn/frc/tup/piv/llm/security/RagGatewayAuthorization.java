package ar.edu.utn.frc.tup.piv.llm.security;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validación de identidad gateway→servicio para RAG (`/api/llm/rag/**`). Mismo patrón M2M que
 * {@link TutorGatewayAuthorization}/{@link GoldenSetAuthorization}, pero con su propia property:
 * RAG es una épica distinta (EP-09) del tutor (EP-05) — un scope propio evita acoplar permisos de
 * una funcionalidad a la otra (decisión tomada al planificar el port de `demoLLMSpringAi`). No se
 * edita `TutorGatewayAuthorization` para no tocar código de otro dueño ([[no-tocar-codigo-ajeno]]). */
@Component
public class RagGatewayAuthorization {
  private final String trustedService;
  private final String requiredScope;
  private final boolean workbench;
  private final UUID workbenchUser;

  public RagGatewayAuthorization(@Value("${llm.rag.trusted-service}") String trustedService,
      @Value("${llm.rag.required-scope}") String requiredScope,
      @Value("${llm.workbench.enabled:false}") boolean workbench,
      @Value("${llm.workbench.user-id:11111111-1111-1111-1111-111111111111}") UUID workbenchUser) {
    this.trustedService = trustedService;
    this.requiredScope = requiredScope;
    this.workbench = workbench;
    this.workbenchUser = workbenchUser;
  }

  public CallerIdentity require(HttpHeaders headers) {
    if (workbench) {
      return new CallerIdentity("workbench", workbenchUser, headers.getFirst("X-Request-Id"), headers.getFirst("traceparent"));
    }
    String serviceId = headers.getFirst("X-Service-Id");
    String scopes = headers.getFirst("X-Service-Scopes");
    String delegated = headers.getFirst("X-Delegated-User");
    if (!trustedService.equals(serviceId) || scopes == null
        || Arrays.stream(scopes.split("\\s+")).noneMatch(requiredScope::equals) || delegated == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El actor no puede invocar el RAG");
    }
    try {
      return new CallerIdentity(serviceId, UUID.fromString(delegated), headers.getFirst("X-Request-Id"), headers.getFirst("traceparent"));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identidad delegada inválida");
    }
  }
}
