package com.astra.engine.telemetry;

import com.astra.engine.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
@Import(GlobalExceptionHandler.class)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TelemetryService telemetryService;

    @Test
    void ingest_shouldReturn200AndResponse_whenPayloadIsValid() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 3.1140, 101.6371, 87.0, 78.0, "RT-001"
        );
        TelemetryIngestResponse response = new TelemetryIngestResponse(
                "V-001", "NOMINAL", null, Instant.parse("2026-10-12T08:30:00Z")
        );
        when(telemetryService.ingest(any(TelemetryIngestRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V-001"))
                .andExpect(jsonPath("$.status").value("NOMINAL"))
                .andExpect(jsonPath("$.intersectedAnomalyId").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").value("2026-10-12T08:30:00Z"));
    }

    @Test
    void ingest_shouldAcceptAliasEndpoint() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 3.1140, 101.6371, 87.0, 78.0, "RT-001"
        );
        TelemetryIngestResponse response = new TelemetryIngestResponse(
                "V-001", "NOMINAL", null, Instant.parse("2026-10-12T08:30:00Z")
        );
        when(telemetryService.ingest(any(TelemetryIngestRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V-001"));
    }

    @Test
    void ingest_shouldReturn400_whenLatitudeIsOutOfKlangValleyBounds() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 5.0000, 101.6371, 87.0, 78.0, "RT-001"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    void ingest_shouldReturn400_whenLongitudeIsOutOfKlangValleyBounds() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 3.1140, 100.0000, 87.0, 78.0, "RT-001"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.longitude").exists());
    }

    @Test
    void ingest_shouldReturn400_whenVehicleIdIsBlank() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "", 3.1140, 101.6371, 87.0, 78.0, "RT-001"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vehicleId").exists());
    }
}
