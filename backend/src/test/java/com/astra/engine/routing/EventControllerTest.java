package com.astra.engine.routing;

import com.astra.engine.common.CorsConfig;
import com.astra.engine.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class})
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NegotiationEventRepository negotiationEventRepository;

    @Test
    void getRecentEvents_returns200AndEventList() throws Exception {
        UUID eventId = UUID.randomUUID();
        NegotiationEvent mockEvent = new NegotiationEvent(
                "D-001",
                "V-001",
                "RT-001",
                "Right-of-way granted to V-001; V-005 yielding via NPE corridor.",
                Instant.parse("2026-11-23T15:30:00Z")
        );
        mockEvent.setId(eventId);

        when(negotiationEventRepository.findTop50ByOrderByCreatedAtDesc())
                .thenReturn(List.of(mockEvent));

        mockMvc.perform(get("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].anomalyId").value("D-001"))
                .andExpect(jsonPath("$[0].vehicleId").value("V-001"))
                .andExpect(jsonPath("$[0].affectedRoutes").value("RT-001"))
                .andExpect(jsonPath("$[0].eventMessage").value("Right-of-way granted to V-001; V-005 yielding via NPE corridor."))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-23T15:30:00Z"));
    }

    @Test
    void getRecentEvents_convenienceAlias_returns200() throws Exception {
        when(negotiationEventRepository.findTop50ByOrderByCreatedAtDesc())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getRecentEvents_corsPreflight_returnsAllowedHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/events")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }
}
