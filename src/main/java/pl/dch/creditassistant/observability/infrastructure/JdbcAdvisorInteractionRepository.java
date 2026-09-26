package pl.dch.creditassistant.observability.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRepository;
import pl.dch.creditassistant.observability.domain.AdvisorInteraction;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

/**
 * PostgreSQL adapter: the interaction row is inserted when the request starts and updated when it completes,
 * so LLM calls and tool invocations can reference it by foreign key while it is running.
 */
@Repository
class JdbcAdvisorInteractionRepository implements AdvisorInteractionRepository {

    private static final String UPSERT = """
            INSERT INTO advisor_interaction (
                interaction_id, started_at, completed_at, masked_advisor_message, masked_final_response,
                status, duration_millis
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (interaction_id) DO UPDATE SET
                completed_at = EXCLUDED.completed_at,
                masked_advisor_message = EXCLUDED.masked_advisor_message,
                masked_final_response = EXCLUDED.masked_final_response,
                status = EXCLUDED.status,
                duration_millis = EXCLUDED.duration_millis
            """;

    private final JdbcTemplate jdbcTemplate;

    JdbcAdvisorInteractionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AdvisorInteraction interaction) {
        jdbcTemplate.update(UPSERT, statement -> {
            statement.setObject(1, interaction.interactionId());
            statement.setTimestamp(2, Timestamp.from(interaction.startedAt()));
            statement.setTimestamp(3, timestampOrNull(interaction.completedAt()));
            statement.setString(4, interaction.maskedAdvisorMessage());
            statement.setString(5, interaction.maskedFinalResponse());
            statement.setString(6, interaction.status() == null ? null : interaction.status().name());
            statement.setObject(7, interaction.durationMillis(), Types.BIGINT);
        });
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
