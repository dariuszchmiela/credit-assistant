package pl.dch.creditassistant.observability.application;

import pl.dch.creditassistant.observability.domain.ToolInvocation;

/**
 * Persistence port for actual agent tool executions.
 */
public interface ToolInvocationRepository {

    void save(ToolInvocation invocation);
}
