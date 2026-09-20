package ar.edu.utn.frc.tup.piv.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.LangChain4jModelAdapter;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.application.service.ModelInvocationService;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.InvalidModelResponseException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelTimeoutException;
import ar.edu.utn.frc.tup.piv.llm.provider.fake.FakeAutoConfiguration;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

@EnabledIfSystemProperty(named = "integration", matches = "true")
class ModelInvocationBddIntegrationTest {
  private final ApplicationContextRunner fakeEnabledRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(FakeAutoConfiguration.class))
      .withPropertyValues("llm.provider.fake.enabled=true");

  @Test
  void validFakeSeededByMigrationReturnsAResponseAcceptedByTheTutorSchema() {
    try (var fixture = migratedFixture()) {
      fakeEnabledRunner.run(context -> {
        var service = fixture.serviceWith(context.getBean(AiProviderAdapter.class));
        var deployment = fixture.deploymentAssignedToTutor();

        assertThat(deployment.providerKey()).isEqualTo("fake");
        assertThat(deployment.modelId()).isEqualTo("fake-socratic-v1");

        var result = service.invoke(ModelFunction.TUTOR, "system", "como ordeno una lista", Duration.ofSeconds(1));

        assertThat(result.provider()).isEqualTo("fake");
        assertThat(result.model()).isEqualTo("fake-socratic-v1");
        assertThat(result.text()).isNotBlank();
      });
    }
  }

  @Test
  void invalidFakeDeploymentIsRejectedByTheRuntimeSchemaValidation() {
    try (var fixture = migratedFixture()) {
      fakeEnabledRunner.run(context -> {
        var service = fixture.serviceWith(context.getBean(AiProviderAdapter.class));
        fixture.assignTutorToTestDeployment(context.getBean(AiProviderAdapter.class), "fake-invalid-v1", 1);
        var deployment = fixture.deploymentAssignedToTutor();

        assertThat(deployment.providerKey()).isEqualTo("fake");
        assertThat(deployment.modelId()).isEqualTo("fake-invalid-v1");

        assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofSeconds(1)))
            .isInstanceOf(InvalidModelResponseException.class);
      });
    }
  }

  @Test
  void slowFakeDeploymentTimesOutThroughThePersistedRuntimeConfiguration() {
    try (var fixture = migratedFixture()) {
      fakeEnabledRunner.run(context -> {
        var service = fixture.serviceWith(context.getBean(AiProviderAdapter.class));
        fixture.assignTutorToTestDeployment(context.getBean(AiProviderAdapter.class), "fake-slow-v1", 1);
        var deployment = fixture.deploymentAssignedToTutor();

        assertThat(deployment.providerKey()).isEqualTo("fake");
        assertThat(deployment.modelId()).isEqualTo("fake-slow-v1");

        assertThatThrownBy(() -> service.invoke(ModelFunction.TUTOR, "system", "pregunta", Duration.ofMillis(50)))
            .isInstanceOf(ModelTimeoutException.class);
      });
    }
  }

  private Fixture migratedFixture() {
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    postgres.start();
    Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas("llm").defaultSchema("llm").createSchemas(true).load().migrate();

    var dataSource = new DriverManagerDataSource(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    var jdbc = new JdbcTemplate(dataSource);
    var json = new ObjectMapper();
    return new Fixture(postgres, new FunctionModelConfigRepository(jdbc),
        new ProviderCredentialRepository(jdbc, json), json);
  }

  private static final class Fixture implements AutoCloseable {
    private final PostgreSQLContainer<?> postgres;
    private final FunctionModelConfigRepository configs;
    private final ProviderCredentialRepository deployments;
    private final ObjectMapper json;

    private Fixture(PostgreSQLContainer<?> postgres, FunctionModelConfigRepository configs,
        ProviderCredentialRepository deployments, ObjectMapper json) {
      this.postgres = postgres;
      this.configs = configs;
      this.deployments = deployments;
      this.json = json;
    }

    private ModelInvocationService serviceWith(AiProviderAdapter fake) {
      var gateway = new ProviderInvocationGateway(
          new ProviderRegistry(List.of(fake)), mock(EncryptedSecretService.class), json);
      var port = new LangChain4jModelAdapter(deployments, configs, gateway);
      return new ModelInvocationService(configs, port);
    }

    private ProviderCredentialRepository.Deployment deploymentAssignedToTutor() {
      var config = configs.find(ModelFunction.TUTOR).orElseThrow();
      assertThat(config.enabled()).isTrue();
      return deployments.forId(config.modelDeploymentId()).orElseThrow();
    }

    private void assignTutorToTestDeployment(AiProviderAdapter fake, String modelId, int slot) {
      var seeded = deploymentAssignedToTutor();
      var model = new ModelDescriptor(modelId, modelId, "bdd-test",
          fake.descriptor().capabilities(), Map.of());
      var deployment = deployments.createCandidate(seeded.credentialId(), model, slot);
      configs.upsert(ModelFunction.TUTOR, deployment.id(),
          new CallerIdentity("bdd-test", UUID.randomUUID(), "request-1", "trace-1"));
    }

    @Override
    public void close() {
      postgres.close();
    }
  }
}
