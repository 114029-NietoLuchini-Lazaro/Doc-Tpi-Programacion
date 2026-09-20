package ar.edu.utn.frc.tup.piv.llm;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.LangChain4jModelAdapter;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.application.service.ModelInvocationService;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.provider.fake.FakeAutoConfiguration;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.AiProviderAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "integration", matches = "true")
class FlywaySchemaTest {
  private static final UUID SEEDED_FAKE_DEPLOYMENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000620622");
  private static final UUID SEEDED_FAKE_CREDENTIAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000172622");

  @Test void migratesPostgresAndArchivesV1BeforeEnforcingV2IntegrityRules() throws Exception {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
      postgres.start();
      Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .schemas("llm").defaultSchema("llm").createSchemas(true).load().migrate();
      try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
           var statement = connection.createStatement()) {
        var tables = statement.executeQuery("select count(*) from information_schema.tables where table_schema = 'llm'");
        tables.next();
        assertThat(tables.getInt(1)).isGreaterThanOrEqualTo(20);

        var legacyTables = statement.executeQuery("select count(*) from information_schema.tables where table_schema = 'legacy_v1' and table_name in ('golden_sets', 'golden_set_entries', 'rubric_versions', 'rubric_dimensions')");
        legacyTables.next();
        assertThat(legacyTables.getInt(1)).isEqualTo(4);
        var activeLegacyTables = statement.executeQuery("select count(*) from information_schema.tables where table_schema = 'llm' and table_name in ('golden_sets', 'golden_set_entries', 'rubric_versions', 'rubric_dimensions')");
        activeLegacyTables.next();
        assertThat(activeLegacyTables.getInt(1)).isZero();

        var fakeSeed = statement.executeQuery("""
            select c.id, c.provider_key, c.state::text, d.id, d.provider_key, d.model_id, f.enabled
            from llm.function_model_config f
            join llm.model_deployments d on d.id = f.model_deployment_id
            join llm.provider_credentials c on c.id = d.credential_id
            where f.function = 'tutor'
            """);
        assertThat(fakeSeed.next()).isTrue();
        assertThat(fakeSeed.getObject("id", UUID.class)).isEqualTo(SEEDED_FAKE_CREDENTIAL_ID);
        assertThat(fakeSeed.getString("provider_key")).isEqualTo("fake");
        assertThat(fakeSeed.getString("state")).isEqualTo("ACTIVE");
        assertThat(fakeSeed.getObject(4, UUID.class)).isEqualTo(SEEDED_FAKE_DEPLOYMENT_ID);
        assertThat(fakeSeed.getString("model_id")).isEqualTo("fake-socratic-v1");
        assertThat(fakeSeed.getBoolean("enabled")).isTrue();

        UUID courseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID rubricFamilyId = UUID.randomUUID();
        UUID rubricVersionId = UUID.randomUUID();
        statement.executeUpdate("insert into llm.rubric_families (id, scope, course_id, name, created_by_user_id) values ('" + rubricFamilyId + "', 'COURSE', '" + courseId + "', 'Rúbrica curso', '" + actorId + "')");
        statement.executeUpdate("insert into llm.rubric_version_v2 (id, family_id, version_no, name, state, created_by_user_id, published_at) values ('" + rubricVersionId + "', '" + rubricFamilyId + "', 1, 'Rúbrica curso', 'PUBLISHED', '" + actorId + "', now())");
        assertThatThrownBy(() -> statement.executeUpdate("update llm.rubric_version_v2 set revision = 2 where id = '" + rubricVersionId + "'"))
            .isInstanceOf(SQLException.class);

        UUID goldenFamilyId = UUID.randomUUID();
        UUID goldenVersionId = UUID.randomUUID();
        statement.executeUpdate("insert into llm.golden_set_families (id, scope, course_id, name, created_by_user_id) values ('" + goldenFamilyId + "', 'COURSE', '" + courseId + "', 'Set curso', '" + actorId + "')");
        statement.executeUpdate("insert into llm.golden_set_versions (id, family_id, version_no, state, created_by_user_id, published_at) values ('" + goldenVersionId + "', '" + goldenFamilyId + "', 1, 'PUBLISHED', '" + actorId + "', now())");
        assertThatThrownBy(() -> statement.executeUpdate("insert into llm.golden_set_cases (golden_set_version_id, case_order, transcript, challenge_context, author, reference_scores) values ('" + goldenVersionId + "', 0, '[{\"role\":\"STUDENT\",\"content\":\"hola\"}]', '{\"statement\":\"x\"}', 'test', '{\"autonomy\":80}')"))
            .isInstanceOf(SQLException.class);

        UUID firstRunId = UUID.randomUUID();
        UUID secondRunId = UUID.randomUUID();
        statement.executeUpdate("insert into llm.calibration_runs (id, course_id, rubric_version_id, golden_set_version_id, model_deployment_id, reason, created_by_user_id) values ('" + firstRunId + "', '" + courseId + "', '" + rubricVersionId + "', '" + goldenVersionId + "', '" + SEEDED_FAKE_DEPLOYMENT_ID + "', 'MANUAL', '" + actorId + "')");
        statement.executeUpdate("insert into llm.calibration_runs (id, course_id, rubric_version_id, golden_set_version_id, model_deployment_id, reason, created_by_user_id) values ('" + secondRunId + "', '" + courseId + "', '" + rubricVersionId + "', '" + goldenVersionId + "', '" + SEEDED_FAKE_DEPLOYMENT_ID + "', 'MANUAL', '" + actorId + "')");
        statement.executeUpdate("insert into llm.active_calibrations (course_id, calibration_run_id, activated_by_user_id) values ('" + courseId + "', '" + firstRunId + "', '" + actorId + "')");
        assertThatThrownBy(() -> statement.executeUpdate("insert into llm.active_calibrations (course_id, calibration_run_id, activated_by_user_id) values ('" + courseId + "', '" + secondRunId + "', '" + actorId + "')"))
            .isInstanceOf(SQLException.class);
      }
    }
  }

  @Test
  void seededFakeTutorDeploymentDrivesRuntimeLookupAndCanBeReassigned() {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
      postgres.start();
      Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .schemas("llm").defaultSchema("llm").createSchemas(true).load().migrate();

      var dataSource = new DriverManagerDataSource(
          postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      var jdbc = new JdbcTemplate(dataSource);
      var json = new ObjectMapper();
      var configs = new FunctionModelConfigRepository(jdbc);
      var deployments = new ProviderCredentialRepository(jdbc, json);

      var config = configs.find(ModelFunction.TUTOR).orElseThrow();
      assertThat(config.enabled()).isTrue();
      assertThat(config.modelDeploymentId()).isEqualTo(SEEDED_FAKE_DEPLOYMENT_ID);

      var deployment = deployments.forId(config.modelDeploymentId()).orElseThrow();
      assertThat(deployment.providerKey()).isEqualTo("fake");
      assertThat(deployment.modelId()).isEqualTo("fake-socratic-v1");
      assertThat(deployment.credentialId()).isEqualTo(SEEDED_FAKE_CREDENTIAL_ID);

      fakeEnabledRunner().run(context -> {
        var service = serviceFor(configs, deployments, context.getBean(AiProviderAdapter.class), json);

        var result = service.invoke(ModelFunction.TUTOR, "system", "como pruebo un array", Duration.ofSeconds(1));

        assertThat(result.provider()).isEqualTo("fake");
        assertThat(result.model()).isEqualTo("fake-socratic-v1");
        assertThat(result.text()).isNotBlank();
      });

      UUID reassignedDeployment = UUID.fromString("00000000-0000-0000-0000-000000620623");
      jdbc.update("""
          insert into llm.model_deployments(
            id, credential_id, provider_key, adapter_version, model_id, model_version, state, capabilities,
            evaluator_state)
          values (?, ?, 'fake', '1', 'fake-socratic-v1', 'dynamic-test', 'ENABLED', '{}'::jsonb, 'CANDIDATE')
          """, reassignedDeployment, SEEDED_FAKE_CREDENTIAL_ID);
      configs.upsert(ModelFunction.TUTOR, reassignedDeployment,
          new CallerIdentity("test-service", UUID.randomUUID(), "request-1", "trace-1"));

      assertThat(configs.find(ModelFunction.TUTOR).orElseThrow().modelDeploymentId())
          .isEqualTo(reassignedDeployment);
    }
  }

  private ApplicationContextRunner fakeEnabledRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FakeAutoConfiguration.class))
        .withPropertyValues("llm.provider.fake.enabled=true");
  }

  private ModelInvocationService serviceFor(FunctionModelConfigRepository configs,
      ProviderCredentialRepository deployments, AiProviderAdapter fake, ObjectMapper json) {
    var gateway = new ProviderInvocationGateway(
        new ProviderRegistry(List.of(fake)), mock(EncryptedSecretService.class), json);
    var port = new LangChain4jModelAdapter(deployments, configs, gateway);
    return new ModelInvocationService(configs, port);
  }
}
