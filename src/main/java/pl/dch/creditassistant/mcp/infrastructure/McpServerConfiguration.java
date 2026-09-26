package pl.dch.creditassistant.mcp.infrastructure;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FR-006: MCP server (official MCP Java SDK) using the Streamable HTTP transport.
 * The transport is a servlet registered in the same embedded web server as the REST API.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpServerProperties.class)
class McpServerConfiguration {

    @Bean
    McpJsonMapper mcpJsonMapper() {
        return McpJsonDefaults.getMapper();
    }

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(
            McpJsonMapper mcpJsonMapper,
            McpServerProperties properties
    ) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
                .mcpEndpoint(properties.endpoint())
                .securityValidator(hostAndOriginValidator(properties))
                .build();
    }

    /**
     * DNS-rebinding protection of the MCP SDK: rejects untrusted Host (421) and Origin (403) headers.
     */
    private DefaultServerTransportSecurityValidator hostAndOriginValidator(McpServerProperties properties) {
        return DefaultServerTransportSecurityValidator.builder()
                .allowedHosts(properties.allowedHosts())
                .allowedOrigins(properties.allowedOrigins())
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider mcpTransportProvider,
            McpServerProperties properties
    ) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(mcpTransportProvider, properties.endpoint());
        registration.setAsyncSupported(true);

        return registration;
    }

    @Bean(destroyMethod = "closeGracefully")
    McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider mcpTransportProvider,
            McpJsonMapper mcpJsonMapper,
            McpServerProperties properties,
            CreditMcpTools creditMcpTools
    ) {
        return McpServer.sync(mcpTransportProvider)
                .serverInfo(properties.name(), properties.version())
                .jsonMapper(mcpJsonMapper)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(creditMcpTools.toolSpecifications(mcpJsonMapper))
                .build();
    }
}
