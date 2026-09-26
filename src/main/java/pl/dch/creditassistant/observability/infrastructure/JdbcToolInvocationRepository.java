package pl.dch.creditassistant.observability.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.dch.creditassistant.observability.application.ToolInvocationRepository;
import pl.dch.creditassistant.observability.domain.ToolInvocation;

import java.sql.Timestamp;

@Repository
class JdbcToolInvocationRepository implements ToolInvocationRepository {

    private static final String INSERT = """
            INSERT INTO tool_invocation (
                tool_invocation_id, advisor_interaction_id, tool_name, started_at, duration_millis, status
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    JdbcToolInvocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ToolInvocation invocation) {
        jdbcTemplate.update(INSERT, statement -> {
            statement.setObject(1, invocation.toolInvocationId());
            statement.setObject(2, invocation.advisorInteractionId());
            statement.setString(3, invocation.toolName());
            statement.setTimestamp(4, Timestamp.from(invocation.startedAt()));
            statement.setLong(5, invocation.durationMillis());
            statement.setString(6, invocation.status().name());
        });
    }
}
