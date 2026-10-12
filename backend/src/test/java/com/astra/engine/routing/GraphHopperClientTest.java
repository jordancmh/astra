package com.astra.engine.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphHopperClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private GraphHopperClient client;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("http://localhost:8989");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new GraphHopperClient(restClientBuilder.build());
    }

    @Test
    void route_successfulResponse_returnsPath() {
        String jsonResponse = """
                {
                  "paths": [
                    {
                      "distance": 34250.5,
                      "time": 1980000,
                      "points": {
                        "type": "LineString",
                        "coordinates": [
                          [101.4496, 3.0430],
                          [101.7116, 3.1579]
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8989/route"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GraphHopperRouteResponse.GraphHopperPath path = client.route(
                101.4496, 3.0430, 101.7116, 3.1579, List.of()
        );

        assertThat(path).isNotNull();
        assertThat(path.distance()).isEqualTo(34250.5);
        assertThat(path.time()).isEqualTo(1980000L);
        assertThat(path.points().coordinates()).hasSize(2);
        mockServer.verify();
    }

    @Test
    void route_emptyPaths_throwsRouteNotFoundException() {
        String jsonResponse = """
                {
                  "paths": []
                }
                """;

        mockServer.expect(requestTo("http://localhost:8989/route"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.route(101.4496, 3.0430, 101.7116, 3.1579, List.of()))
                .isInstanceOf(RouteNotFoundException.class)
                .hasMessageContaining("No route found");
        mockServer.verify();
    }

    @Test
    void route_serverError_throwsGraphHopperServiceException() {
        mockServer.expect(requestTo("http://localhost:8989/route"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.route(101.4496, 3.0430, 101.7116, 3.1579, List.of()))
                .isInstanceOf(GraphHopperServiceException.class);
        mockServer.verify();
    }
}
