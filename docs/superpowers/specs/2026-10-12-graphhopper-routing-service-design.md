# GraphHopper Routing Service Design Specification

## 1. Overview & Objectives
This specification addresses GitHub Issue #9: **"Integrate GraphHopper API in Spring Boot"**.
The service calculates optimal vehicular routes across the Klang Valley road network (`Lat: 2.90 to 3.30`, `Lng: 101.40 to 101.90`) by querying a local GraphHopper container sidecar, converting the geometric path into a GeoJSON `LineString`, and exposing the result via the REST endpoint `POST /api/v1/routes/calculate` (and `/api/routes/calculate`).

## 2. Architecture & Design Principles
- **Decoupled Modular Monolith:** Located strictly inside package `com.astra.engine.routing` (with shared GeoJSON models in `com.astra.engine.common` or `routing`).
- **Separation of Concerns:**
  - `GraphHopperClient`: Dedicated HTTP client wrapping Spring's fluent `RestClient`. Manages external GraphHopper protocol details, payload generation, and response parsing.
  - `RoutingService`: High-level domain service orchestrating coordinate validation, vehicle routing requests, and DTO assembly.
  - `RouteController`: Exposes Spring MVC REST endpoints.
- **Strict Coordinate Conventions:**
  - Standard GeoJSON and GraphHopper coordinate order: `[longitude, latitude]`.
  - All input coordinates must pass Bean Validation for the Klang Valley bounding box (`lat` in `[2.90, 3.30]`, `lng` in `[101.40, 101.90]`).
- **No Entity Leaks:** Controllers and services return clean Java record DTOs.

## 3. Infrastructure & Environment Configuration
### 3.1 Docker Compose GraphHopper Service Fix
In `docker-compose.yml`, GraphHopper startup parameter must use `--graph-cache` (or `-o`):
```yaml
graphhopper:
  image: israelhikingmap/graphhopper:latest
  container_name: astra-graphhopper
  ports:
    - "8989:8989"
  volumes:
    - ./routing-data:/data
  command: ["--input", "/data/klang-valley.osm.pbf", "--config", "/data/config.yml", "--graph-cache", "/data/graph-cache"]
  restart: unless-stopped
```

### 3.2 Spring Boot Configuration (`application.yml`)
```yaml
graphhopper:
  base-url: ${GRAPHHOPPER_URL:http://localhost:8989}
  timeout-ms: ${GRAPHHOPPER_TIMEOUT_MS:5000}
```

## 4. API & Data Contracts

### 4.1 REST Request Contract (`POST /api/v1/routes/calculate` & `POST /api/routes/calculate`)
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

### 4.2 GeoJSON LineString Model
```java
package com.astra.engine.common;

import java.util.List;

public record GeoJsonLineString(
    String type, // "LineString"
    List<List<Double>> coordinates // [[lng, lat], [lng, lat], ...]
) {
    public GeoJsonLineString(List<List<Double>> coordinates) {
        this("LineString", coordinates);
    }
}
```

### 4.3 REST Response Contract (`200 OK`)
```java
package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;

public record RouteCalculateResponse(
    String routeId,
    String status, // "OPTIMAL"
    Double distanceMeters,
    Long estimatedTimeMs,
    GeoJsonLineString geometry
) {}
```

### 4.4 GraphHopper Internal Request & Response Models
```java
package com.astra.engine.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GraphHopperRouteRequest(
    String profile, // "car"
    List<List<Double>> points, // [[originLng, originLat], [destLng, destLat]]
    @JsonProperty("points_encoded") boolean pointsEncoded, // false
    @JsonProperty("ch.disable") boolean chDisable, // true
    @JsonProperty("custom_model") GraphHopperCustomModel customModel // null for now
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

public record GraphHopperCustomModel(
    List<Object> speed,
    Object areas
) {}

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

## 5. Component Implementations

### 5.1 `GraphHopperConfig`
Configures a Spring `@Bean RestClient graphHopperRestClient` initialized with the configured `graphhopper.base-url` and timeouts.

### 5.2 `GraphHopperClient`
- Injects `RestClient` and GraphHopper properties.
- Method: `GraphHopperRouteResponse.GraphHopperPath route(double originLng, double originLat, double destLng, double destLat, List<String> avoidAnomalyIds)`
- Sends `POST /route` with `GraphHopperRouteRequest`.
- Validates that `response != null && response.paths() != null && !response.paths().isEmpty()`.
- Throws `RouteNotFoundException` if no paths exist or GraphHopper returns an empty path list.
- Throws `GraphHopperServiceException` if an HTTP communication error or timeout occurs.

### 5.3 `RoutingService`
- Injects `GraphHopperClient`.
- Method: `RouteCalculateResponse calculateRoute(RouteCalculateRequest request)`
- Calls `graphHopperClient.route(request.originLng(), request.originLat(), request.destLng(), request.destLat(), request.avoidAnomalyIds())`.
- Converts the path's points coordinates into `GeoJsonLineString`.
- Returns `new RouteCalculateResponse(request.routeId(), "OPTIMAL", path.distance(), path.time(), geoJsonLineString)`.

### 5.4 `RouteController`
- Annotated with `@RestController` and `@RequestMapping({"/api/v1/routes", "/api/routes"})`.
- Exposes `@PostMapping("/calculate")`.
- Accepts `@Valid @RequestBody RouteCalculateRequest request`.
- Returns `ResponseEntity.ok(routingService.calculateRoute(request))`.

### 5.5 Exception Handling (`GlobalExceptionHandler`)
- `RouteNotFoundException` -> mapped to HTTP 404 with structured JSON response `{ "status": 404, "error": "Not Found", "message": "..." }`.
- `GraphHopperServiceException` -> mapped to HTTP 503 with structured JSON response `{ "status": 503, "error": "Service Unavailable", "message": "..." }`.

## 6. Testing Strategy
1. **`GraphHopperClientTest` (Unit Test):**
   - Uses `MockRestServiceServer`.
   - Asserts outgoing request format (`points`, `points_encoded: false`, `ch.disable: true`).
   - Asserts deserialization of distance, time, and coordinates.
   - Asserts error handling on empty paths and server error responses.
2. **`RouteControllerTest` (Web Layer Unit Test):**
   - Uses `@WebMvcTest(RouteController.class)`.
   - Validates successful calculation (200 OK) on both `/api/v1/routes/calculate` and `/api/routes/calculate`.
   - Validates Bean Validation failure on missing fields (400 Bad Request).
   - Validates Bean Validation failure on out-of-bounds coordinates (400 Bad Request).
3. **`RoutingIntegrationTest` (End-to-End Integration Test):**
   - Connects to the live GraphHopper container sidecar on `http://localhost:8989`.
   - Calculates a route between Port Klang (`101.4496, 3.0430`) and KL City Centre (`101.7116, 3.1579`).
   - Verifies HTTP 200, status "OPTIMAL", distance > 0, time > 0, and valid `LineString` coordinates array.
