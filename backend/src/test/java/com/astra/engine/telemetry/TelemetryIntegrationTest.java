package com.astra.engine.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelemetryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleTelemetryRepository repository;

    @Test
    void ingestTelemetry_shouldPersistEntityToPostGisAndReturn200() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-TEST-99", 3.1140, 101.6371, 90.0, 60.0, "RT-100"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V-TEST-99"))
                .andExpect(jsonPath("$.status").value("NOMINAL"))
                .andExpect(jsonPath("$.updatedAt").exists());

        List<VehicleTelemetry> logs = repository.findByVehicleIdOrderByRecordedAtDesc("V-TEST-99");
        assertThat(logs).isNotEmpty();
        VehicleTelemetry saved = logs.get(0);
        assertThat(saved.getVehicleId()).isEqualTo("V-TEST-99");
        assertThat(saved.getRouteId()).isEqualTo("RT-100");
        assertThat(saved.getCoordinates().getSRID()).isEqualTo(4326);
        assertThat(saved.getCoordinates().getX()).isEqualTo(101.6371);
        assertThat(saved.getCoordinates().getY()).isEqualTo(3.1140);
        assertThat(saved.getSpeedKmh()).isEqualTo(60.0);
        assertThat(saved.getBearing()).isEqualTo(90.0);
    }
}
