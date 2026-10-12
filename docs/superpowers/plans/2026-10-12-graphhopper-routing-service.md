# GraphHopper Routing Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the backend service layer and REST controller (`POST /api/v1/routes/calculate` and `/api/routes/calculate`) integrating the self-hosted GraphHopper engine to generate optimal route geometries as GeoJSON objects in the Klang Valley.

**Architecture:** A dedicated `GraphHopperClient` uses Spring's `RestClient` to dispatch `POST /route` requests (`points_encoded: false`, `ch.disable: true`) to the GraphHopper sidecar container (`http://localhost:8989`). `RoutingService` coordinates bounding-box validated origin/destination coordinates and converts GraphHopper paths into standard `GeoJsonLineString` DTOs exposed via `RouteController`.

**Tech Stack:** Java 21, Spring Boot 4.0.8, Spring MVC, Spring `RestClient`, Bean Validation (`spring-boot-starter-validation`), Jackson, GraphHopper 9.x Docker sidecar, PostGIS 16-3.4.

## Global Constraints

- **Language & Framework:** Java 21, Spring Boot 4.0.8.
- **Package Location:** Module package `com.astra.engine.routing` (shared DTOs in `com.astra.engine.common`).
- **Coordinate Order:** Strictly `[longitude, latitude]` for GeoJSON, JTS, and GraphHopper requests (`new Coordinate(lng, lat)`, `[lng, lat]`).
- **Klang Valley Bounding Box:** Lat in `[2.90, 3.30]`, Lng in `[101.40, 101.90]`.
- **GraphHopper Query Flags:** `"points_encoded": false`, `"ch.disable": true`.
- **DTO Safety:** Never expose raw JPA entities or JTS geometries directly from `@RestController`; use Java records.
- **Database Port:** PostGIS host port `5433` (`5433:5432`).
- **Test Strategy:** Follow TDD (Red-Green-Refactor) for each task.

---

### Task 1: Fix GraphHopper Service in `docker-compose.yml` & Configure `application.yml`

**Files:**
- Modify: `docker-compose.yml:28`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: Live GraphHopper server at `http://localhost:8989` responding to HTTP requests, and `graphhopper.base-url` + `graphhopper.timeout-ms` in Spring configuration.

- [ ] **Step 1: Update `docker-compose.yml` GraphHopper command argument**

Replace `--graph-location` with `--graph-cache`:
```yaml
    command: ["--input", "/data/klang-valley.osm.pbf", "--config", "/data/config.yml", "--graph-cache", "/data/graph-cache"]
```

- [ ] **Step 2: Start GraphHopper container and verify health**

Run:
```bash
docker compose up -d graphhopper
sleep 5
docker compose ps graphhopper
curl -s http://localhost:8989/health
```
Expected: Container is `Up` and `curl -s http://localhost:8989/health` returns `{"status":"ok"}`.

- [ ] **Step 3: Add GraphHopper properties to `backend/src/main/resources/application.yml`**

Append to `backend/src/main/resources/application.yml`:
```yaml
graphhopper:
  base-url: ${GRAPHHOPPER_URL:http://localhost:8989}
  timeout-ms: ${GRAPHHOPPER_TIMEOUT_MS:5000}
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml backend/src/main/resources/application.yml
git commit -m "chore(infra): fix graphhopper graph-cache arg in docker-compose and configure properties"
```

---

### Task 2: Implement GeoJSON DTOs, Request/Response Records, Exceptions & Global Handler

**Files:**
- Create: `backend/src/main/java/com/astra/engine/common/GeoJsonLineString.java`
- Create: `backend/src/main/java/com/astra/engine/routing/RouteCalculateRequest.java`
- Create: `backend/src/main/java/com/astra/engine/routing/RouteCalculateResponse.java`
- Create: `backend/src/main/java/com/astra/engine/routing/GraphHopperRouteRequest.java`
- Create: `backend/src/main/java/com/astra/engine/routing/GraphHopperRouteResponse.java`
- Create: `backend/src/main/java/com/astra/engine/routing/GraphHopperCustomModel.java`
- Create: `backend/src/main/java/com/astra/engine/routing/RouteNotFoundException.java`
- Create: `backend/src/main/java/com/astra/engine/routing/GraphHopperServiceException.java`
- Modify: `backend/src/main/java/com/astra/engine/common/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/astra/engine/routing/RouteCalculateRequestValidationTest.java`

**Interfaces:**
- Produces: `GeoJsonLineString`, `RouteCalculateRequest`, `RouteCalculateResponse`, `GraphHopperRouteRequest`, `GraphHopperRouteResponse`, `RouteNotFoundException`, `GraphHopperServiceException`.

- [ ] **Step 1: Write failing validation test `RouteCalculateRequestValidationTest`**

Create `backend/src/test/java/com/astra/engine/routing/RouteCalculateRequestValidationTest.java`:
```java
package com.astra.engine.routing;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCalculateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validRequest_passesValidation() {
        RouteCalculateRequest request = new RouteCalculateRequest(
                "RT-001", "V-001",
                3.0430, 101.4496,
                3.1579, 101.7116,
                List.of()
        );
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void originOutOfKlangValley_failsValidation() {
        RouteCalculateRequest request = new RouteCalculateRequest(
                "RT-001", "V-001",
                2.5000, 101.4496,
                3.1579, 101.7116,
                List.of()
        );
        assertThat(validator.validate(request)).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RouteCalculateRequestValidationTest`
Expected: Compilation failure because `RouteCalculateRequest` does not exist yet.

- [ ] **Step 3: Create GeoJSON and DTO Records**

Create `backend/src/main/java/com/astra/engine/common/GeoJsonLineString.java`:
```java
package com.astra.engine.common;

import java.util.List;

public record GeoJsonLineString(
        String type,
        List<List<Double>> coordinates
) {
    public GeoJsonLineString(List<List<Double>> coordinates) {
        this("LineString", coordinates);
    }
}
```

Create `backend/src/main/java/com/astra/engine/routing/RouteCalculateRequest.java`:
```java
package com.astra.engine.routing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RouteCalculateRequest(
        @NotBlank(message = "routeId is required")
        String routeId,

        @NotBlank(message = "vehicleId is required")
        String vehicleId,

        @NotNull(message = "originLat is required")
        @DecimalMin(value = "2.90", message = "originLat must be >= 2.90")
        @DecimalMax(value = "3.30", message = "originLat must be <= 3.30")
        Double originLat,

        @NotNull(message = "originLng is required")
        @DecimalMin(value = "101.40", message = "originLng must be >= 101.40")
        @DecimalMax(value = "101.90", message = "originLng must be <= 101.90")
        Double originLng,

        @NotNull(message = "destLat is required")
        @DecimalMin(value = "2.90", message = "destLat must be >= 2.90")
        @DecimalMax(value = "3.30", message = "destLat must be <= 3.30")
        Double destLat,

        @NotNull(message = "destLng is required")
        @DecimalMin(value = "101.40", message = "destLng must be >= 101.40")
        @DecimalMax(value = "101.90", message = "destLng must be <= 101.90")
        Double destLng,

        List<String> avoidAnomalyIds
) {
    public RouteCalculateRequest {
        if (avoidAnomalyIds == null) {
            avoidAnomalyIds = List.of();
        }
    }
}
```

Create `backend/src/main/java/com/astra/engine/routing/RouteCalculateResponse.java`:
```java
package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;

public record RouteCalculateResponse(
        String routeId,
        String status,
        Double distanceMeters,
        Long estimatedTimeMs,
        GeoJsonLineString geometry
) {}
```

Create `backend/src/main/java/com/astra/engine/routing/GraphHopperCustomModel.java`:
```java
package com.astra.engine.routing;

import java.util.List;

public record GraphHopperCustomModel(
        List<Object> speed,
        Object areas
) {}
```

Create `backend/src/main/java/com/astra/engine/routing/GraphHopperRouteRequest.java`:
```java
package com.astra.engine.routing;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GraphHopperRouteRequest(
        String profile,
        List<List<Double>> points,
        @JsonProperty("points_encoded") boolean pointsEncoded,
        @JsonProperty("ch.disable") boolean chDisable,
        @JsonProperty("custom_model") GraphHopperCustomModel customModel
) {
    public static GraphHopperRouteRequest of(double originLng, double originLat, double destLng, double destLat) {
        return new GraphHopperRouteRequest(
                "car",
                List.of(List.of(originLng, originLat), List.of(destLng, destLat)),
                false,
                true,
                null
        );
    }
}
```

Create `backend/src/main/java/com/astra/engine/routing/GraphHopperRouteResponse.java`:
```java
package com.astra.engine.routing;

import java.util.List;

public record GraphHopperRouteResponse(
        List<GraphHopperPath> paths
) {
    public record GraphHopperPath(
            Double distance,
            Long time,
            GraphHopperPoints points
    ) {}

    public record GraphHopperPoints(
            String type,
            List<List<Double>> coordinates
    ) {}
}
```

Create `backend/src/main/java/com/astra/engine/routing/RouteNotFoundException.java`:
```java
package com.astra.engine.routing;

public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/com/astra/engine/routing/GraphHopperServiceException.java`:
```java
package com.astra.engine.routing;

public class GraphHopperServiceException extends RuntimeException {
    public GraphHopperServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphHopperServiceException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Update `GlobalExceptionHandler.java`**

Add exception handlers for `RouteNotFoundException` (HTTP 404) and `GraphHopperServiceException` (HTTP 503) to `backend/src/main/java/com/astra/engine/common/GlobalExceptionHandler.java`:
```java
    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRouteNotFoundException(RouteNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(GraphHopperServiceException.class)
    public ResponseEntity<Map<String, Object>> handleGraphHopperServiceException(GraphHopperServiceException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
```

- [ ] **Step 5: Run validation test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RouteCalculateRequestValidationTest`
Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/astra/engine/common/GeoJsonLineString.java \
        backend/src/main/java/com/astra/engine/routing/ \
        backend/src/main/java/com/astra/engine/common/GlobalExceptionHandler.java \
        backend/src/test/java/com/astra/engine/routing/RouteCalculateRequestValidationTest.java
git commit -m "feat(routing): implement GeoJSON models, DTO records, exception handlers, and validation tests"
```

---

### Task 3: Implement `GraphHopperConfig` & `GraphHopperClient` with Unit Tests

**Files:**
- Create: `backend/src/main/java/com/astra/engine/routing/GraphHopperConfig.java`
- Create: `backend/src/main/java/com/astra/engine/routing/GraphHopperClient.java`
- Test: `backend/src/test/java/com/astra/engine/routing/GraphHopperClientTest.java`

**Interfaces:**
- Consumes: `GraphHopperRouteRequest`, `GraphHopperRouteResponse`, `RouteNotFoundException`, `GraphHopperServiceException`
- Produces: `GraphHopperClient` bean with method:
  `GraphHopperRouteResponse.GraphHopperPath route(double originLng, double originLat, double destLng, double destLat, List<String> avoidAnomalyIds)`

- [ ] **Step 1: Write failing unit test `GraphHopperClientTest`**

Create `backend/src/test/java/com/astra/engine/routing/GraphHopperClientTest.java`:
```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GraphHopperClientTest`
Expected: Compilation failure because `GraphHopperClient` does not exist yet.

- [ ] **Step 3: Implement `GraphHopperConfig` & `GraphHopperClient`**

Create `backend/src/main/java/com/astra/engine/routing/GraphHopperConfig.java`:
```java
package com.astra.engine.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class GraphHopperConfig {

    @Value("${graphhopper.base-url:http://localhost:8989}")
    private String baseUrl;

    @Value("${graphhopper.timeout-ms:5000}")
    private int timeoutMs;

    @Bean
    public RestClient graphHopperRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
```

Create `backend/src/main/java/com/astra/engine/routing/GraphHopperClient.java`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GraphHopperClientTest`
Expected: `BUILD SUCCESS`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/astra/engine/routing/GraphHopperConfig.java \
        backend/src/main/java/com/astra/engine/routing/GraphHopperClient.java \
        backend/src/test/java/com/astra/engine/routing/GraphHopperClientTest.java
git commit -m "feat(routing): implement GraphHopperClient with RestClient and MockRestServiceServer tests"
```

---

### Task 4: Implement `RoutingService` and `RouteController` with Unit & Integration Tests

**Files:**
- Create: `backend/src/main/java/com/astra/engine/routing/RoutingService.java`
- Create: `backend/src/main/java/com/astra/engine/routing/RouteController.java`
- Test: `backend/src/test/java/com/astra/engine/routing/RouteControllerTest.java`
- Test: `backend/src/test/java/com/astra/engine/routing/RoutingIntegrationTest.java`

**Interfaces:**
- Consumes: `GraphHopperClient`, `RouteCalculateRequest`, `RouteCalculateResponse`, `GeoJsonLineString`
- Produces: `RoutingService.calculateRoute(RouteCalculateRequest)`, `RouteController` exposing `POST /api/v1/routes/calculate` and `POST /api/routes/calculate`.

- [ ] **Step 1: Write failing unit test `RouteControllerTest`**

Create `backend/src/test/java/com/astra/engine/routing/RouteControllerTest.java`:
```java
package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.server.MockServletWebServerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RouteControllerTest`
Expected: Compilation failure because `RouteController` and `RoutingService` do not exist yet.

- [ ] **Step 3: Implement `RoutingService` & `RouteController`**

Create `backend/src/main/java/com/astra/engine/routing/RoutingService.java`:
```java
package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {

    private final GraphHopperClient graphHopperClient;

    public RoutingService(GraphHopperClient graphHopperClient) {
        this.graphHopperClient = graphHopperClient;
    }

    public RouteCalculateResponse calculateRoute(RouteCalculateRequest request) {
        GraphHopperRouteResponse.GraphHopperPath path = graphHopperClient.route(
                request.originLng(),
                request.originLat(),
                request.destLng(),
                request.destLat(),
                request.avoidAnomalyIds()
        );

        GeoJsonLineString geometry = new GeoJsonLineString(path.points().coordinates());

        return new RouteCalculateResponse(
                request.routeId(),
                "OPTIMAL",
                path.distance(),
                path.time(),
                geometry
        );
    }
}
```

Create `backend/src/main/java/com/astra/engine/routing/RouteController.java`:
```java
package com.astra.engine.routing;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/routes", "/api/routes"})
public class RouteController {

    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<RouteCalculateResponse> calculateRoute(@Valid @RequestBody RouteCalculateRequest request) {
        RouteCalculateResponse response = routingService.calculateRoute(request);
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=RouteControllerTest`
Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Write End-to-End Integration Test `RoutingIntegrationTest`**

Create `backend/src/test/java/com/astra/engine/routing/RoutingIntegrationTest.java`:
```java
package com.astra.engine.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void calculateRoute_liveGraphHopper_returnsValidGeoJsonRoute() {
        RouteCalculateRequest request = new RouteCalculateRequest(
                "RT-INTEG-001",
                "V-001",
                3.0430, 101.4496, // Port Klang
                3.1579, 101.7116, // KL City Centre
                List.of()
        );

        ResponseEntity<RouteCalculateResponse> response = restTemplate.postForEntity(
                "/api/v1/routes/calculate",
                request,
                RouteCalculateResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RouteCalculateResponse body = response.getBody();
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
```

- [ ] **Step 6: Run full test suite across the backend**

Run: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS` with all unit and integration tests passing.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/astra/engine/routing/RoutingService.java \
        backend/src/main/java/com/astra/engine/routing/RouteController.java \
        backend/src/test/java/com/astra/engine/routing/RouteControllerTest.java \
        backend/src/test/java/com/astra/engine/routing/RoutingIntegrationTest.java
git commit -m "feat(routing): implement RoutingService, RouteController, and end-to-end integration tests"
```
