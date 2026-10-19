package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import com.astra.engine.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.astra.engine.common.CorsConfig;

@WebMvcTest(RouteController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class})
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutingService routingService;

    @Test
    void calculateRoute_validPayload_returns200AndCalculatedRoute() throws Exception {
        RouteCalculateResponse mockResponse = new RouteCalculateResponse(
                "RT-001",
                "OPTIMAL",
                34250.5,
                1980000L,
                new GeoJsonLineString(List.of(List.of(101.4496, 3.0430), List.of(101.7116, 3.1579)))
        );

        when(routingService.calculateRoute(any())).thenReturn(mockResponse);

        String jsonPayload = """
                {
                  "routeId": "RT-001",
                  "vehicleId": "V-001",
                  "originLat": 3.0430,
                  "originLng": 101.4496,
                  "destLat": 3.1579,
                  "destLng": 101.7116,
                  "avoidAnomalyIds": []
                }
                """;

        mockMvc.perform(post("/api/v1/routes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value("RT-001"))
                .andExpect(jsonPath("$.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.distanceMeters").value(34250.5))
                .andExpect(jsonPath("$.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.geometry.coordinates[0][0]").value(101.4496));
    }

    @Test
    void calculateRoute_aliasEndpoint_returns200() throws Exception {
        RouteCalculateResponse mockResponse = new RouteCalculateResponse(
                "RT-001",
                "OPTIMAL",
                34250.5,
                1980000L,
                new GeoJsonLineString(List.of(List.of(101.4496, 3.0430), List.of(101.7116, 3.1579)))
        );

        when(routingService.calculateRoute(any())).thenReturn(mockResponse);

        String jsonPayload = """
                {
                  "routeId": "RT-001",
                  "vehicleId": "V-001",
                  "originLat": 3.0430,
                  "originLng": 101.4496,
                  "destLat": 3.1579,
                  "destLng": 101.7116
                }
                """;

        mockMvc.perform(post("/api/routes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());
    }

    @Test
    void calculateRoute_outOfBoundsOriginLat_returns400() throws Exception {
        String invalidPayload = """
                {
                  "routeId": "RT-001",
                  "vehicleId": "V-001",
                  "originLat": 2.5000,
                  "originLng": 101.4496,
                  "destLat": 3.1579,
                  "destLng": 101.7116
                }
                """;

        mockMvc.perform(post("/api/v1/routes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.originLat").exists());
    }

    @Test
    void calculateRoute_missingRouteId_returns400() throws Exception {
        String invalidPayload = """
                {
                  "vehicleId": "V-001",
                  "originLat": 3.0430,
                  "originLng": 101.4496,
                  "destLat": 3.1579,
                  "destLng": 101.7116
                }
                """;

        mockMvc.perform(post("/api/v1/routes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.routeId").exists());
    }

    @Test
    void calculateRoute_corsPreflight_returns200WithCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/routes/calculate")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
