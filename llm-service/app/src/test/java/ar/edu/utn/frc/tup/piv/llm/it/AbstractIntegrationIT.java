package ar.edu.utn.frc.tup.piv.llm.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de los tests de integración: app completa contra un PostgreSQL (pgvector) real con todas las
 * migraciones Flyway. El contenedor es único por JVM y se comparte entre clases (arranca una vez).
 * Requiere Docker; el gate de cobertura de `mvn verify` cuenta con estos tests.
 */
@SpringBootTest(properties = {
    "llm.credentials.master-key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
    "app.jwks-refresh-ms=3600000",
    "spring.task.scheduling.enabled=false",
    // spring.task.scheduling.enabled no apaga @Scheduled: espaciamos los workers para que no le
    // roben corridas/evaluaciones en cola a los tests que las manejan a mano.
    "llm.calibrations.dispatch-delay-ms=3600000",
    "llm.evaluations.resume-delay-ms=3600000",
    // El shadow (E-31) también lo maneja a mano su IT.
    "llm.shadow.dispatch-delay-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationIT {

  protected static final java.util.UUID TEACHER = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");

  @org.springframework.beans.factory.annotation.Autowired protected org.springframework.test.web.servlet.MockMvc mvc;
  @org.springframework.beans.factory.annotation.Autowired protected com.fasterxml.jackson.databind.ObjectMapper json;

  /** Identidad de docente de un curso, tal como la propaga el API Gateway (admin-service delegando). */
  protected static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asTeacher(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, java.util.UUID courseId) {
    // Reemplaza, no acumula: `asTeacher(request, X)` declara la matrícula de ESE request, igual que
    // hacía el header `X-Teacher-Course-Ids`. Así los casos de "otro curso" siguen dando 403.
    CURSOS_DEL_DOCENTE.clear();
    CURSOS_DEL_DOCENTE.add(courseId);
    return request
        .header("X-Principal-Type", "service")
        .header("X-Service-Id", "admin-service")
        .header("X-Service-Scopes", "llm.golden-set.manage llm.rubric-template.manage llm.tutor.interact llm.rag.query")
        .header("X-Delegated-User", TEACHER.toString())
        .header("X-Actor-Id", TEACHER.toString())
        .header("X-User-Roles", "TEACHER")
        .header("X-Teacher-Course-Ids", courseId.toString())
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
  }

  protected com.fasterxml.jackson.databind.JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start();
  }

  /**
   * Cohortes en las que el docente de prueba está matriculado. `asTeacher(request, courseId)` la
   * completa: el propio test declara a qué curso pertenece, igual que antes lo hacía con el header
   * `X-Teacher-Course-Ids`.
   */
  static final java.util.Set<java.util.UUID> CURSOS_DEL_DOCENTE =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  /**
   * Stub de courses-service. Desde la integración main↔dev la autorización de curso ya no se
   * resuelve con headers: `CourseAuthorization` consulta a Courses por el Gateway
   * ({@code GatewayCoursesMembershipClient}). Sin este stub todos los ITs de curso responden 503.
   * Devuelve matrícula solo para el docente de prueba y solo en las cohortes que el test declaró,
   * para que los casos de "otro curso" sigan dando 403.
   */
  static final com.sun.net.httpserver.HttpServer COURSES_STUB;

  static {
    try {
      COURSES_STUB = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
      COURSES_STUB.createContext("/api/courses", exchange -> {
        String[] partes = exchange.getRequestURI().getPath().split("/");
        // /api/courses/{courseCohortId}/members/{userId}
        boolean matriculado = false;
        if (partes.length >= 6 && "members".equals(partes[4])) {
          try {
            matriculado = TEACHER.equals(java.util.UUID.fromString(partes[5]))
                && CURSOS_DEL_DOCENTE.contains(java.util.UUID.fromString(partes[3]));
          } catch (IllegalArgumentException noEsUuid) {
            matriculado = false;
          }
        }
        byte[] cuerpo = matriculado
            ? "{\"role\":\"TEACHER\",\"status\":\"ACTIVE\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            : "{\"error\":\"not a member\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(matriculado ? 200 : 404, cuerpo.length);
        exchange.getResponseBody().write(cuerpo);
        exchange.close();
      });
      COURSES_STUB.start();
    } catch (java.io.IOException error) {
      throw new IllegalStateException("No se pudo levantar el stub de courses-service", error);
    }
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("llm.courses.base-url", () -> "http://localhost:" + COURSES_STUB.getAddress().getPort());
  }
}
