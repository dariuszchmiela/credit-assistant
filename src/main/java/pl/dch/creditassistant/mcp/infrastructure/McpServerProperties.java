package pl.dch.creditassistant.mcp.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param endpoint       HTTP path of the MCP Streamable HTTP endpoint, served by the application's web server
 * @param name           server name announced to MCP clients during initialization
 * @param version        server version announced to MCP clients during initialization
 * @param allowedHosts   accepted {@code Host} header values; exact ({@code localhost:8080}) or any port ({@code localhost:*})
 * @param allowedOrigins accepted {@code Origin} header values when a client sends one;
 *                       exact ({@code http://localhost:8080}) or any port ({@code http://localhost:*})
 */
@ConfigurationProperties("mcp.server")
public record McpServerProperties(
        String endpoint,
        String name,
        String version,
        List<String> allowedHosts,
        List<String> allowedOrigins
) {
}
