package com.astra.engine.mcp;

import com.astra.engine.routing.RouteRepository;
import com.astra.engine.telemetry.VehicleTelemetryRepository;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
class McpServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private McpTools mcpTools;

    @Autowired
    private McpSyncServer mcpSyncServer;

    @MockitoBean
    private javax.sql.DataSource dataSource;

    @MockitoBean
    private VehicleTelemetryRepository vehicleTelemetryRepository;

    @MockitoBean
    private RouteRepository routeRepository;

    @Test
    void contextLoads_withMcpServerAndTools() {
        assertThat(mcpTools).isNotNull();
        assertThat(mcpSyncServer).isNotNull();

        List<McpSchema.Tool> tools = mcpSyncServer.listTools();
        assertThat(tools).hasSize(2);

        List<String> registeredTools = tools.stream()
                .map(McpSchema.Tool::name)
                .toList();
        assertThat(registeredTools).containsExactlyInAnyOrder("getFleetState", "updateRoute");
        assertThat(mcpSyncServer.getServerInfo().name()).isEqualTo("astra-mcp-server");
    }

    @Test
    void sseEndpoint_shouldRespondToOptionsWithCorsHeaders() throws Exception {
        mockMvc.perform(options("/sse")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
