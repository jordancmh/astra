package com.astra.engine.telemetry;

import com.astra.engine.common.GeoJsonLineString;
import com.astra.engine.telemetry.dto.FleetRouteDto;
import com.astra.engine.telemetry.dto.FleetStateResponse;
import com.astra.engine.telemetry.dto.FleetSummaryDto;
import com.astra.engine.telemetry.dto.FleetVehicleDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FleetController.class)
class FleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FleetService fleetService;

    @Test
    void getFleetState_shouldReturnFleetStateResponse() throws Exception {
        FleetSummaryDto summary = new FleetSummaryDto(1, 1, 0, 0);
        FleetVehicleDto vehicle = new FleetVehicleDto(
                "V-001",
                "RT-001",
                3.0833,
                101.5833,
                90.0,
                60.0,
                5,
                "NOMINAL",
                null,
                Instant.now()
        );
        FleetRouteDto route = new FleetRouteDto(
                "RT-001",
                "V-001",
                "OPTIMAL",
                new GeoJsonLineString(List.of(List.of(101.58, 3.08), List.of(101.60, 3.10))),
                5000.0,
                300000L
        );
        FleetStateResponse response = new FleetStateResponse(
                summary,
                List.of(vehicle),
                List.of(route),
                Instant.now()
        );

        when(fleetService.getFleetState()).thenReturn(response);

        mockMvc.perform(get("/api/v1/fleet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.nominal").value(1))
                .andExpect(jsonPath("$.vehicles[0].id").value("V-001"))
                .andExpect(jsonPath("$.vehicles[0].cargoPriority").value(5))
                .andExpect(jsonPath("$.vehicles[0].status").value("NOMINAL"))
                .andExpect(jsonPath("$.routes[0].id").value("RT-001"));

        mockMvc.perform(get("/api/fleet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1));
    }
}
