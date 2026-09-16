package ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Inbox deduplication. It is transactional with the consumer's domain writes. */
@Repository
public class ProcessedEventRepository {
  private final JdbcTemplate jdbc;

  public ProcessedEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public boolean claim(UUID eventId, String consumer) {
    return jdbc.update("""
        insert into llm.processed_events(event_id, consumer) values (?, ?)
        on conflict (event_id, consumer) do nothing
        """, eventId, consumer) == 1;
  }
}
