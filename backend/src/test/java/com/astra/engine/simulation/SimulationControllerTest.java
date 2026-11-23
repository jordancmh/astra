package com.astra.engine.simulation;

import com.astra.engine.simulation.dto.SimulationStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    @Test
    void seedFleet_shouldReturn200() throws Exception {
        when(simulationService.seedFleet()).thenReturn(new SimulationStatusResponse(false, 5, 0, "Seeded 5 demo vehicles successfully"));

        mockMvc.perform(post("/api/v1/simulation/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount").value(5))
                .andExpect(jsonPath("$.running").value(false));
    }

    @Test
    void startSimulation_shouldReturn200() throws Exception {
        when(simulationService.startSimulation()).thenReturn(new SimulationStatusResponse(true, 5, 1, "Running"));

        mockMvc.perform(post("/api/v1/simulation/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    void stopSimulation_shouldReturn200() throws Exception {
        when(simulationService.stopSimulation()).thenReturn(new SimulationStatusResponse(false, 5, 10, "Paused"));

        mockMvc.perform(post("/api/v1/simulation/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));
    }

    @Test
    void resetSimulation_shouldReturn200() throws Exception {
        when(simulationService.resetSimulation()).thenReturn(new SimulationStatusResponse(false, 0, 0, "Simulation reset complete"));

        mockMvc.perform(delete("/api/v1/simulation/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount").value(0));
    }

    @Test
    void getStatus_shouldReturn200() throws Exception {
        when(simulationService.getStatus()).thenReturn(new SimulationStatusResponse(false, 0, 0, "Paused"));

        mockMvc.perform(get("/api/v1/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));
    }
}
