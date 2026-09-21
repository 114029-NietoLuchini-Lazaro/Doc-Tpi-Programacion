package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.GoldenSetUpdateProposalRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldenSetUpdateProposalServiceTest {

  @Test
  void proposesUpdatesAfterABaseVersionIsPublished() {
    var repository = mock(GoldenSetUpdateProposalRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var expirations = mock(CalibrationExpirationService.class);
    var service = new GoldenSetUpdateProposalService(repository, jdbc, expirations);

    UUID baseVersion = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    when(jdbc.queryForObject(eq("select family_id from llm.golden_set_versions where id = ?"),
        eq(UUID.class), eq(baseVersion))).thenReturn(familyId);
    when(repository.createForPublishedBase(baseVersion)).thenReturn(3);

    assertThat(service.proposeForPublishedBase(baseVersion)).isEqualTo(3);
    verify(repository).createForPublishedBase(baseVersion);
  }

  /**
   * Épica 364: publicar una base nueva invalida las calibraciones activas de esa familia antes de
   * proponer las actualizaciones. El test original de `dev` no cubría este paso porque la expiración
   * llegó por `main`; se agrega al recuperarlo en la integración del 2026-09-21.
   */
  @Test
  void expiresTheActiveCalibrationsOfTheFamilyBeforeProposing() {
    var repository = mock(GoldenSetUpdateProposalRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var expirations = mock(CalibrationExpirationService.class);
    var service = new GoldenSetUpdateProposalService(repository, jdbc, expirations);

    UUID baseVersion = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    when(jdbc.queryForObject(eq("select family_id from llm.golden_set_versions where id = ?"),
        eq(UUID.class), eq(baseVersion))).thenReturn(familyId);

    service.proposeForPublishedBase(baseVersion);

    verify(expirations).expireByGoldenSet(familyId);
  }
}
