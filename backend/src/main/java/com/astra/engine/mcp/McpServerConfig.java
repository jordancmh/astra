package com.astra.engine.mcp;

import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public McpSyncServerCustomizer mcpSyncServerCustomizer() {
        return spec -> {
            // Allows runtime customizer hooks for the MCP sync server
        };
    }
}
