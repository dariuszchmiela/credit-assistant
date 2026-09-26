package pl.dch.creditassistant.mcp.infrastructure;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import pl.dch.creditassistant.TestcontainersConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-006, MCP test (SPEC 87): a real MCP client talks to the running application over Streamable HTTP.
 * Does not require Ollama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class McpServerProtocolIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"security-test","version":"1"}}}""";

    @LocalServerPort
    private int port;

    @Autowired
    private McpServerProperties mcpServerProperties;

    private McpSyncClient mcpClient;

    @BeforeEach
    void connect() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint(mcpServerProperties.endpoint())
                .build();

        mcpClient = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @AfterEach
    void disconnect() {
        mcpClient.closeGracefully();
    }

    @Test
    void shouldInitializeAndListTheThreeCreditTools() {
        InitializeResult initializeResult = mcpClient.initialize();
        ListToolsResult tools = mcpClient.listTools();

        assertThat(initializeResult.serverInfo().name()).isEqualTo(mcpServerProperties.name());
        assertThat(tools.tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("getContractStatus", "calculateInstallment", "checkEligibility");
        assertThat(tools.tools())
                .allSatisfy(tool -> assertThat(tool.outputSchema()).isNotEmpty());
    }

    @Test
    void shouldCalculateInstallmentOverTheMcpProtocol() {
        mcpClient.initialize();

        CallToolResult result = mcpClient.callTool(new CallToolRequest(
                "calculateInstallment",
                Map.of("principal", 100000, "annualInterestRate", 8.5, "months", 60),
                null));

        Map<String, Object> structuredContent = structuredContent(result);
        assertThat(decimal(structuredContent.get("monthlyInstallment"))).isEqualByComparingTo("2051.65");
        assertThat(decimal(structuredContent.get("totalRepayment"))).isEqualByComparingTo("123099.00");
        assertThat(result.content()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, text -> assertThat(text.text()).contains("2051.65"));
    }

    @Test
    void shouldReturnContractDataAndUnknownContractOverTheMcpProtocol() {
        mcpClient.initialize();

        Map<String, Object> knownContract = structuredContent(mcpClient.callTool(
                new CallToolRequest("getContractStatus", Map.of("contractNumber", "CTR-1001"), null)));
        Map<String, Object> unknownContract = structuredContent(mcpClient.callTool(
                new CallToolRequest("getContractStatus", Map.of("contractNumber", "CTR-9999"), null)));

        assertThat(knownContract)
                .containsEntry("contractNumber", "CTR-1001")
                .containsEntry("found", true)
                .containsEntry("status", "ACTIVE")
                .containsEntry("nextPaymentDate", "2026-10-15");
        assertThat(decimal(knownContract.get("outstandingPrincipal"))).isEqualByComparingTo("85000.00");
        assertThat(unknownContract)
                .containsEntry("contractNumber", "CTR-9999")
                .containsEntry("found", false)
                .containsEntry("status", null);
    }

    @Test
    void shouldReturnAuthoritativeEligibilityDecisionOverTheMcpProtocol() {
        mcpClient.initialize();

        Map<String, Object> rejected = structuredContent(mcpClient.callTool(new CallToolRequest(
                "checkEligibility",
                Map.of("monthlyIncome", 8000, "existingMonthlyObligations", 5000, "requestedLoanAmount", 20000),
                null)));

        assertThat(rejected)
                .containsEntry("decision", "NOT_ELIGIBLE")
                .containsEntry("reasonCode", "OBLIGATIONS_TOO_HIGH");
    }

    @Test
    void shouldReportInvalidInstallmentInputAsToolErrorOverTheMcpProtocol() {
        mcpClient.initialize();

        CallToolResult result = mcpClient.callTool(new CallToolRequest(
                "calculateInstallment",
                Map.of("principal", 100000, "annualInterestRate", 8.5, "months", 0),
                null));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).singleElement()
                .isInstanceOfSatisfying(TextContent.class,
                        text -> assertThat(text.text()).isEqualTo("months must be greater than zero"));
    }

    @Test
    void shouldAcceptConfiguredLocalhostHostAndOrigin() throws IOException {
        int status = postInitialize("localhost:" + port, "http://localhost:" + port);

        assertThat(status).isEqualTo(200);
    }

    @Test
    void shouldRejectUntrustedOrigin() throws IOException {
        int status = postInitialize("localhost:" + port, "http://attacker.example");

        assertThat(status).as("Origin validation of the MCP SDK").isEqualTo(403);
    }

    @Test
    void shouldRejectUntrustedHost() throws IOException {
        int status = postInitialize("attacker.example:" + port, null);

        assertThat(status).as("Host validation of the MCP SDK (DNS rebinding)").isEqualTo(421);
    }

    @Test
    void shouldKeepServingTheRestChatApiNextToTheMcpEndpoint() throws IOException, InterruptedException {
        HttpRequest invalidChatRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"\"}"))
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpResponse<String> response = httpClient.send(invalidChatRequest, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).as("request validation by ChatController, no LLM call").isEqualTo(400);
        }
    }

    /**
     * Sends a raw MCP initialize request, because the JDK HTTP client does not allow overriding the Host header.
     *
     * @return HTTP status code of the response
     */
    private int postInitialize(String hostHeader, String originHeader) throws IOException {
        byte[] body = INITIALIZE_REQUEST.getBytes(StandardCharsets.UTF_8);
        String headers = "POST " + mcpServerProperties.endpoint() + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + (originHeader == null ? "" : "Origin: " + originHeader + "\r\n")
                + "Content-Type: application/json\r\n"
                + "Accept: application/json, text/event-stream\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout((int) REQUEST_TIMEOUT.toMillis());
            OutputStream output = socket.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine = reader.readLine();

            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> structuredContent(CallToolResult result) {
        assertThat(result.isError()).as("tool error: %s", result.content()).isNotEqualTo(Boolean.TRUE);

        return (Map<String, Object>) result.structuredContent();
    }

    private BigDecimal decimal(Object jsonNumber) {
        return new BigDecimal(jsonNumber.toString());
    }
}
