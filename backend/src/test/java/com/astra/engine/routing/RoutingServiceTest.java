package com.astra.engine.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private GraphHopperClient graphHopperClient;

    @Mock
    private RouteRepository routeRepository;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(graphHopperClient, routeRepository);
    }

    @Test
    void updateRoute_shouldPersistRouteAndReturnResult() {
        List<List<Double>> coords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        when(routeRepository.findById("RT-001")).thenReturn(Optional.empty());
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteUpdateResult result = routingService.updateRoute("V-001", "RT-001", coords, "Detour via NPE");

        assertThat(result.status()).isEqualTo("COMMITTED");
        assertThat(result.vehicleId()).isEqualTo("V-001");
        assertThat(result.routeId()).isEqualTo("RT-001");
        assertThat(result.coordinatesCount()).isEqualTo(2);
        assertThat(result.geometry().coordinates()).isEqualTo(coords);

        ArgumentCaptor<Route> captor = ArgumentCaptor.forClass(Route.class);
        verify(routeRepository).save(captor.capture());
        Route saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("RT-001");
        assertThat(saved.getStatus()).isEqualTo("OPTIMAL");
        assertThat(saved.getPathGeometry().getSRID()).isEqualTo(4326);
    }
}
