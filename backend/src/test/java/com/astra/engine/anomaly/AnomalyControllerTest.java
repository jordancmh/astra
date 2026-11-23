package com.astra.engine.anomaly;

import com.astra.engine.anomaly.dto.AnomalyClearResponse;
import com.astra.engine.anomaly.dto.AnomalyDto;
import com.astra.engine.anomaly.dto.AnomalyInjectRequest;
import com.astra.engine.anomaly.dto.AnomalyInjectResponse;
import com.astra.engine.common.GeoJsonPolygon;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnomalyController.class)
@Import(GlobalExceptionHandler.class)
class AnomalyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnomalyService anomalyService;

    @Test
    void injectAnomaly_shouldReturn201_whenPayloadIsValid() throws Exception {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "ROAD_CLOSURE", "HIGH", 3.1180, 101.6210, 600.0
        );
        AnomalyInjectResponse response = new AnomalyInjectResponse(
                "D-001", "ROAD_CLOSURE", "HIGH",
                List.of("RT-003"), List.of("V-003"),
                Instant.parse("2026-10-12T08:34:12Z")
        );
        when(anomalyService.injectAnomaly(any(AnomalyInjectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/anomalies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anomalyId").value("D-001"))
                .andExpect(jsonPath("$.anomalyType").value("ROAD_CLOSURE"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.affectedRouteIds[0]").value("RT-003"))
                .andExpect(jsonPath("$.affectedVehicleIds[0]").value("V-003"));
    }

    @Test
    void injectAnomaly_shouldAcceptAliasEndpoint() throws Exception {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "CONGESTION", "MEDIUM", 3.1180, 101.6210, 500.0
        );
        AnomalyInjectResponse response = new AnomalyInjectResponse(
                "D-002", "CONGESTION", "MEDIUM",
                List.of(), List.of(),
                Instant.now()
        );
        when(anomalyService.injectAnomaly(any(AnomalyInjectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/anomalies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anomalyId").value("D-002"));
    }

    @Test
    void injectAnomaly_shouldReturn400_whenLatitudeIsOutOfKlangValleyBounds() throws Exception {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "ROAD_CLOSURE", "HIGH", 5.0000, 101.6210, 600.0
        );

        mockMvc.perform(post("/api/v1/anomalies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    void injectAnomaly_shouldReturn400_whenLongitudeIsOutOfKlangValleyBounds() throws Exception {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "ROAD_CLOSURE", "HIGH", 3.1180, 100.0000, 600.0
        );

        mockMvc.perform(post("/api/v1/anomalies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.longitude").exists());
    }

    @Test
    void injectAnomaly_shouldReturn400_whenAnomalyTypeIsBlank() throws Exception {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "", "HIGH", 3.1180, 101.6210, 600.0
        );

        mockMvc.perform(post("/api/v1/anomalies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.anomalyType").exists());
    }

    @Test
    void getActiveAnomalies_shouldReturn200AndList() throws Exception {
        GeoJsonPolygon poly = GeoJsonPolygon.ofSingleRing(List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124),
                List.of(101.615, 3.112)
        ));
        AnomalyDto dto = new AnomalyDto(
                "D-001", "D-001", "ROAD_CLOSURE", "HIGH", 600.0,
                List.of(101.6210, 3.1180), poly, Instant.now(), true
        );

        when(anomalyService.getActiveAnomalies()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("D-001"))
                .andExpect(jsonPath("$[0].anomalyType").value("ROAD_CLOSURE"))
                .andExpect(jsonPath("$[0].center[0]").value(101.6210))
                .andExpect(jsonPath("$[0].center[1]").value(3.1180))
                .andExpect(jsonPath("$[0].polygon.type").value("Polygon"));
    }

    @Test
    void deleteAnomaly_shouldReturn200_whenDeleted() throws Exception {
        when(anomalyService.deleteAnomaly("D-001")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/anomalies/D-001"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAnomaly_shouldReturn404_whenNotFound() throws Exception {
        when(anomalyService.deleteAnomaly("D-999")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/anomalies/D-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearAllAnomalies_shouldReturn200AndCount() throws Exception {
        when(anomalyService.clearAllAnomalies())
                .thenReturn(new AnomalyClearResponse(3, Instant.now()));

        mockMvc.perform(delete("/api/v1/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearedCount").value(3));
    }
}
