package com.astra.engine.routing;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class GraphHopperClient {

    private final RestClient restClient;

    public GraphHopperClient(RestClient graphHopperRestClient) {
        this.restClient = graphHopperRestClient;
    }

    public GraphHopperRouteResponse.GraphHopperPath route(
            double originLng, double originLat,
            double destLng, double destLat,
            List<String> avoidAnomalyIds
    ) {
        GraphHopperRouteRequest request = GraphHopperRouteRequest.of(
                originLng, originLat, destLng, destLat
        );

        try {
            GraphHopperRouteResponse response = restClient.post()
                    .uri("/route")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GraphHopperRouteResponse.class);

            if (response == null || response.paths() == null || response.paths().isEmpty()) {
                throw new RouteNotFoundException(
                        "No route found between [" + originLng + ", " + originLat + "] and [" + destLng + ", " + destLat + "]"
                );
            }

            return response.paths().get(0);
        } catch (RouteNotFoundException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new GraphHopperServiceException("Failed to query GraphHopper routing service: " + ex.getMessage(), ex);
        }
    }
}
