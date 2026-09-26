package pl.dch.creditassistant.observability.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.dch.creditassistant.observability.application.AiInteractionRepository;
import pl.dch.creditassistant.observability.domain.AiInteraction;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * PostgreSQL adapter of {@link AiInteractionRepository} (one row per LLM call); the table is created by the
 * Flyway migration {@code V1__create_ai_interaction.sql}.
 */
@Repository
class JdbcAiInteractionRepository implements AiInteractionRepository {

    private static final String INSERT = """
            INSERT INTO ai_interaction (
                id, advisor_interaction_id, interaction_timestamp, model_identifier, status, error_type,
                masked_user_prompt, masked_model_response, input_token_count, output_token_count,
                estimated_cost, duration_millis, tool_names
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String TEXT_ARRAY_TYPE = "text";

    private final JdbcTemplate jdbcTemplate;

    JdbcAiInteractionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AiInteraction interaction) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT);
            Array toolNames = connection.createArrayOf(TEXT_ARRAY_TYPE, interaction.toolNames().toArray());

            statement.setObject(1, interaction.id());
            statement.setObject(2, interaction.advisorInteractionId());
            statement.setTimestamp(3, Timestamp.from(interaction.timestamp()));
            statement.setString(4, interaction.modelIdentifier());
            statement.setString(5, interaction.status().name());
            statement.setString(6, interaction.errorType());
            statement.setString(7, interaction.maskedUserPrompt());
            statement.setString(8, interaction.maskedModelResponse());
            statement.setObject(9, interaction.inputTokenCount(), Types.INTEGER);
            statement.setObject(10, interaction.outputTokenCount(), Types.INTEGER);
            statement.setBigDecimal(11, interaction.estimatedCost());
            statement.setLong(12, interaction.durationMillis());
            statement.setArray(13, toolNames);

            return statement;
        });
    }
}
