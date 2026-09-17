package ar.edu.utn.frc.tup.piv.llm.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hace cumplir ADR-019 y reglas estrictas de Clean Architecture / DDD:
 * - domain es el núcleo: no depende de Spring, Kafka, JDBC/JPA, SDKs de modelos, infrastructure, application ni api.
 * - application no depende de la capa de presentación (api).
 * - api no depende directamente de infrastructure.persistence (orquestado vía application).
 * Jackson en domain es una excepción documentada y queda explícitamente permitido.
 */
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("ar.edu.utn.frc.tup.piv.llm");
  }

  @Test
  void domainDoesNotDependOnSpring() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
    rule.check(classes);
  }

  @Test
  void domainDoesNotDependOnKafka() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..");
    rule.check(classes);
  }

  @Test
  void domainDoesNotDependOnProviderSdks() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "dev.langchain4j..",
            "com.openai..",
            "com.anthropic..",
            "com.google.genai..");
    rule.check(classes);
  }

  @Test
  void domainDoesNotDependOnJdbcOrJpa() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.persistence..", "jakarta.persistence..");
    rule.check(classes);
  }

  @Test
  void domainDoesNotDependOnInfrastructure() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
    rule.check(classes);
  }

  @Test
  void domainDoesNotDependOnApplication() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..application..");
    rule.check(classes);
  }

  @Test
  void domainDoesNotDependOnApi() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..api..");
    rule.check(classes);
  }

  @Test
  void applicationDoesNotDependOnApi() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAPackage("..api..");
    rule.check(classes);
  }

  @Test
  void apiDoesNotDependOnInfrastructurePersistence() {
    // ADR-019: los endpoints administrativos de proveedores y calibración institucional
    // acceden temporalmente a persistencia hasta su refactorización en application.
    ArchRule rule = noClasses()
        .that().resideInAPackage("..api..")
        .and().haveNameNotMatching(".*(ProviderCredentialController|InstitutionalCalibrationController).*")
        .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.persistence..");
    rule.check(classes);
  }
}
