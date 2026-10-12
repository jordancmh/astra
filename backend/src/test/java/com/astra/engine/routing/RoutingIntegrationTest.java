package com.astra.engine.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void calculateRoute_liveGraphHopper_returnsValidGeoJsonRoute() throws Exception {
        RouteCalculateRequest request = new RouteCalculateRequest(
                "RT-INTEG-001",
                "V-001",
                3.0430, 101.4496, // Port Klang
                3.1579, 101.7116, // KL City Centre
                List.of()
        );

        MvcResult result = mockMvc.perform(post("/api/v1/routes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        RouteCalculateResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RouteCalculateResponse.class
        );

        assertThat(body).isNotNull();
        assertThat(body.routeId()).isEqualTo("RT-INTEG-001");
        assertThat(body.status()).isEqualTo("OPTIMAL");
        assertThat(body.distanceMeters()).isGreaterThan(20000.0);
        assertThat(body.estimatedTimeMs()).isGreaterThan(0L);
        assertThat(body.geometry()).isNotNull();
        assertThat(body.geometry().type()).isEqualTo("LineString");
        assertThat(body.geometry().coordinates()).isNotEmpty();
        assertThat(body.geometry().coordinates().size()).isGreaterThan(10);
    }
}
