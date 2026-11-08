# Implementation Plan: Implement Java MCP Server (Issue #13)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a production-grade Model Context Protocol (MCP) Server in the Spring Boot `/backend` (`com.astra.engine.mcp`) exposing HTTP/SSE transport on port `8081`. Register two predefined MCP tools—`getFleetState` (reads vehicle telemetry/status) and `updateRoute` (writes detour geometries)—enforcing strict spatial validation (Klang Valley bounds, `[longitude, latitude]` order, SRID 4326) and security perimeter guardrails (NFR 2.1: strictly routing through existing JPA Repositories and Domain Services, rejecting raw SQL).

**Architecture:**
- **MCP Server Layer (`com.astra.engine.mcp`):** Configures an MCP Sync Server with WebMVC Server-Sent Events (SSE) transport (`GET /sse` for SSE event streams, `POST /mcp/message` for JSON-RPC 2.0 requests). Exposes tools to local Python Multi-Agent System (MAS) clients without exposing database credentials.
- **Domain Service Layer (`com.astra.engine.telemetry`, `com.astra.engine.routing`):**
  - `TelemetryService`: Provides read operations for current vehicle telemetry and status via `VehicleTelemetryRepository`.
  - `RoutingService`: Validates and commits negotiated detour routes via `RouteRepository` and JTS `GeometryFactory` (SRID 4326).
- **Persistence Layer (`PostgreSQL + PostGIS`):** Interacts strictly through Spring Data JPA (`VehicleTelemetryRepository`, `RouteRepository`). No raw SQL strings or native unchecked queries are permitted.
- **Shared Spatial Utilities (`com.astra.engine.common`):** `GeometryUtils` extended with `createLineString(List<List<Double>> coordinates)` using explicit `[longitude, latitude]` ordering.

**Tech Stack:**
- Backend: Java 21, Spring Boot 4.x / 3.x, Spring Data JPA, Hibernate Spatial, JTS Core (`org.locationtech.jts:jts-core:1.19.0`), PostgreSQL 16 + PostGIS 3.4.
- MCP Transport: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:2.0.2` (backed by official `io.modelcontextprotocol.sdk:mcp-core:2.1.0`).
- Testing: JUnit 5 (`org.junit.jupiter`), AssertJ, Mockito (`@MockitoBean`), Spring Boot Test (`@SpringBootTest`, `@AutoConfigureMockMvc`).

---

## Global Technical Guardrails

1. **Spatial Standard & Coordinate Order:**
   - Coordinate order is strictly **`[longitude, latitude]`** everywhere: JTS (`new Coordinate(lng, lat)`), GeoJSON, and GraphHopper.
   - All spatial objects use **WGS 84 (`SRID 4326`)**.
   - Bounding box is strictly bounded to the Klang Valley (`Lat: 2.90 to 3.30`, `Lng: 101.40 to 101.90`). Any coordinate outside these bounds must be rejected with validation errors.
2. **Security & Perimeter Isolation (NFR 2.1, NFR 2.2):**
   - No direct database access or raw SQL strings for AI agents.
   - All tool arguments are strongly typed Java records with field-level constraints.
   - All mutations execute through Spring Data JPA repositories with parameterized queries.
3. **JPA & Jackson Serialization (AGENTS.md §6.B):**
   - Never return raw JPA `@Entity` containing JTS `Geometry` from controllers or MCP tools.
   - Map all results to clean Java `record` DTOs converting geometries to standard GeoJSON arrays (`double[]` or `double[][]`).
4. **Decoupled Modular Monolith Package Boundaries:**
   - `com.astra.engine.mcp` communicates with `telemetry` and `routing` exclusively via domain service interfaces, never by directly injecting repositories across modules.
5. **Git Hygiene & Secret Hygiene:**
   - Dedicated branch `feature/13-java-mcp-server`.
   - Never commit API secrets or tokens.

---

## Tasks

### Task 1: Create Dedicated Feature Branch

**Files:**
- Git only

- [ ] **Step 1: Check out `main` and pull latest changes**
```bash
git checkout main
git pull origin main
```

- [ ] **Step 2: Create and checkout feature branch**
```bash
git checkout -b feature/13-java-mcp-server
```

- [ ] **Step 3: Verify git status**
```bash
git status
```
Expected: `On branch feature/13-java-mcp-server`, working tree clean.

---

### Task 2: Add Spring AI MCP Dependencies to `pom.xml`

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add Spring AI BOM to `<dependencyManagement>` and `spring-ai-starter-mcp-server-webmvc` to `<dependencies>`**
Add the following blocks to `backend/pom.xml`:

```xml
	<properties>
		<java.version>21</java.version>
		<spring-ai.version>2.0.2</spring-ai.version>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.ai</groupId>
				<artifactId>spring-ai-bom</artifactId>
				<version>${spring-ai.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
```

Under `<dependencies>`:
```xml
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
		</dependency>
```

- [ ] **Step 2: Verify dependency resolution**
```bash
cd /home/jordan/sunway/capstone/astra/backend
./mvnw dependency:resolve
```
Expected: `BUILD SUCCESS`.

---

### Task 3: Spatial Helper for JTS LineString Construction in `GeometryUtils`

**Files:**
- Modify: `backend/src/main/java/com/astra/engine/common/GeometryUtils.java`
- Modify: `backend/src/test/java/com/astra/engine/common/GeometryUtilsTest.java`

- [ ] **Step 1: Write unit tests in `GeometryUtilsTest.java` for LineString creation and Klang Valley bounds validation**
Add tests verifying:
- Valid coordinate pairs produce a JTS `LineString` with SRID 4326.
- Order is strictly `[longitude, latitude]`.
- Null, empty, or single-point lists throw `IllegalArgumentException`.
- Bounding box validator method `isWithinKlangValley(double longitude, double latitude)` correctly accepts coordinates within `Lat: [2.90, 3.30]`, `Lng: [101.40, 101.90]` and rejects coordinates outside.

- [ ] **Step 2: Implement `createLineString` and `isWithinKlangValley` in `GeometryUtils.java`**
```java
package com.astra.engine.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;

public final class GeometryUtils {

    public static final int SRID = 4326;
    public static final double MIN_LAT = 2.90;
    public static final double MAX_LAT = 3.30;
    public static final double MIN_LNG = 101.40;
    public static final double MAX_LNG = 101.90;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID);

    private GeometryUtils() {
    }

    public static Point createPoint(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public static LineString createLineString(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("LineString requires at least 2 coordinate points");
        }

        Coordinate[] jtsCoordinates = new Coordinate[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coord = coordinates.get(i);
            if (coord == null || coord.size() < 2) {
                throw new IllegalArgumentException("Each coordinate must contain [longitude, latitude]");
            }
            double lng = coord.get(0);
            double lat = coord.get(1);
            if (!isWithinKlangValley(lng, lat)) {
                throw new IllegalArgumentException(String.format(
                        "Coordinate [%f, %f] is outside Klang Valley bounding box (Lat: %.2f..%.2f, Lng: %.2f..%.2f)",
                        lng, lat, MIN_LAT, MAX_LAT, MIN_LNG, MAX_LNG));
            }
            jtsCoordinates[i] = new Coordinate(lng, lat);
        }

        return GEOMETRY_FACTORY.createLineString(jtsCoordinates);
    }

    public static boolean isWithinKlangValley(double longitude, double latitude) {
        return longitude >= MIN_LNG && longitude <= MAX_LNG
                && latitude >= MIN_LAT && latitude <= MAX_LAT;
    }
}
```

- [ ] **Step 3: Run unit tests**
```bash
cd /home/jordan/sunway/capstone/astra/backend
./mvnw test -Dtest=GeometryUtilsTest
```
Expected: All tests pass.

---

### Task 4: Route Entity & RouteRepository in `com.astra.engine.routing`

To support `updateRoute` persisting detour geometries into PostgreSQL/PostGIS (matching `@documents/03-database-erd.md`), scaffold the `Route` entity and its repository.

**Files:**
- Create: `backend/src/main/java/com/astra/engine/routing/Route.java`
- Create: `backend/src/main/java/com/astra/engine/routing/RouteRepository.java`
- Create: `backend/src/test/java/com/astra/engine/routing/RouteRepositoryTest.java`

- [ ] **Step 1: Create `Route.java` JPA entity**
```java
package com.astra.engine.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "route")
public class Route {

    @Id
    private String id;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "origin_point", columnDefinition = "geometry(Point, 4326)")
    private Point originPoint;

    @Column(name = "destination_point", columnDefinition = "geometry(Point, 4326)")
    private Point destinationPoint;

    @Column(name = "path_geometry", columnDefinition = "geometry(LineString, 4326)", nullable = false)
    private LineString pathGeometry;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "estimated_time_ms")
    private Long estimatedTimeMs;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Route() {
    }

    public Route(String id, String status, Point originPoint, Point destinationPoint,
                 LineString pathGeometry, Double distanceMeters, Long estimatedTimeMs) {
        this.id = id;
        this.status = status;
        this.originPoint = originPoint;
        this.destinationPoint = destinationPoint;
        this.pathGeometry = pathGeometry;
        this.distanceMeters = distanceMeters;
        this.estimatedTimeMs = estimatedTimeMs;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Point getOriginPoint() { return originPoint; }
    public void setOriginPoint(Point originPoint) { this.originPoint = originPoint; }
    public Point getDestinationPoint() { return destinationPoint; }
    public void setDestinationPoint(Point destinationPoint) { this.destinationPoint = destinationPoint; }
    public LineString getPathGeometry() { return pathGeometry; }
    public void setPathGeometry(LineString pathGeometry) { this.pathGeometry = pathGeometry; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    public Long getEstimatedTimeMs() { return estimatedTimeMs; }
    public void setEstimatedTimeMs(Long estimatedTimeMs) { this.estimatedTimeMs = estimatedTimeMs; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Create `RouteRepository.java`**
```java
package com.astra.engine.routing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteRepository extends JpaRepository<Route, String> {
}
```

- [ ] **Step 3: Write `@DataJpaTest` integration test in `RouteRepositoryTest.java`**
Verify saving and querying a `Route` with JTS `LineString` (SRID 4326) works against PostGIS.

- [ ] **Step 4: Run repository test**
```bash
cd /home/jordan/sunway/capstone/astra/backend
./mvnw test -Dtest=RouteRepositoryTest
```
Expected: `BUILD SUCCESS`.

---

### Task 5: Domain Services Integration (`TelemetryService` & `RoutingService`)

Extend the domain services to expose clean DTO methods for fleet state retrieval and route updating.

**Files:**
- Modify: `backend/src/main/java/com/astra/engine/telemetry/VehicleTelemetryRepository.java`
- Modify: `backend/src/main/java/com/astra/engine/telemetry/TelemetryService.java`
- Create: `backend/src/main/java/com/astra/engine/telemetry/VehicleStatusDto.java`
- Modify: `backend/src/main/java/com/astra/engine/routing/RoutingService.java`
- Create: `backend/src/main/java/com/astra/engine/routing/RouteUpdateResult.java`

- [ ] **Step 1: Define `VehicleStatusDto.java`**
```java
package com.astra.engine.telemetry;

import java.time.Instant;

public record VehicleStatusDto(
        String vehicleId,
        String routeId,
        double latitude,
        double longitude,
        Double bearing,
        Double speedKmh,
        String status,
        Instant recordedAt
) {}
```

- [ ] **Step 2: Add latest telemetry query in `VehicleTelemetryRepository.java`**
```java
package com.astra.engine.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleTelemetryRepository extends JpaRepository<VehicleTelemetry, UUID> {

    List<VehicleTelemetry> findByVehicleIdOrderByRecordedAtDesc(String vehicleId);

    Optional<VehicleTelemetry> findFirstByVehicleIdOrderByRecordedAtDesc(String vehicleId);

    @Query("SELECT t FROM VehicleTelemetry t WHERE t.recordedAt = " +
           "(SELECT MAX(t2.recordedAt) FROM VehicleTelemetry t2 WHERE t2.vehicleId = t.vehicleId)")
    List<VehicleTelemetry> findLatestTelemetryForAllVehicles();
}
```

- [ ] **Step 3: Add `getFleetState` methods in `TelemetryService.java`**
```java
    @Transactional(readOnly = true)
    public List<VehicleStatusDto> getFleetState(String vehicleId) {
        if (vehicleId != null && !vehicleId.isBlank()) {
            return telemetryRepository.findFirstByVehicleIdOrderByRecordedAtDesc(vehicleId.trim())
                    .map(this::toStatusDto)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return telemetryRepository.findLatestTelemetryForAllVehicles().stream()
                .map(this::toStatusDto)
                .toList();
    }

    private VehicleStatusDto toStatusDto(VehicleTelemetry entity) {
        double lat = entity.getCoordinates() != null ? entity.getCoordinates().getY() : 0.0;
        double lng = entity.getCoordinates() != null ? entity.getCoordinates().getX() : 0.0;
        return new VehicleStatusDto(
                entity.getVehicleId(),
                entity.getRouteId(),
                lat,
                lng,
                entity.getBearing(),
                entity.getSpeedKmh(),
                "NOMINAL",
                entity.getRecordedAt()
        );
    }
```

- [ ] **Step 4: Define `RouteUpdateResult.java`**
```java
package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;

import java.time.Instant;

public record RouteUpdateResult(
        String status,
        String vehicleId,
        String routeId,
        int coordinatesCount,
        GeoJsonLineString geometry,
        Instant updatedAt
) {}
```

- [ ] **Step 5: Add `updateRoute` method in `RoutingService.java`**
```java
    private final RouteRepository routeRepository;

    // Updated constructor with RouteRepository injection
    public RoutingService(GraphHopperClient graphHopperClient, RouteRepository routeRepository) {
        this.graphHopperClient = graphHopperClient;
        this.routeRepository = routeRepository;
    }

    @Transactional
    public RouteUpdateResult updateRoute(String vehicleId, String routeId, List<List<Double>> coordinates, String rationale) {
        LineString pathGeometry = GeometryUtils.createLineString(coordinates);
        Point origin = (Point) pathGeometry.getStartPoint();
        Point destination = (Point) pathGeometry.getEndPoint();

        Route route = routeRepository.findById(routeId).orElseGet(() -> new Route());
        route.setId(routeId);
        route.setStatus("OPTIMAL");
        route.setOriginPoint(origin);
        route.setDestinationPoint(destination);
        route.setPathGeometry(pathGeometry);
        route.setUpdatedAt(Instant.now());

        Route saved = routeRepository.save(route);

        return new RouteUpdateResult(
                "COMMITTED",
                vehicleId,
                saved.getId(),
                coordinates.size(),
                new GeoJsonLineString(coordinates),
                saved.getUpdatedAt()
        );
    }
```

- [ ] **Step 6: Update existing `RoutingService` and `RouteController` tests to pass with new dependency**
Verify existing unit tests (`RoutingIntegrationTest`, `RouteControllerTest`) compile and pass.

---

### Task 6: Implement MCP Tools (`getFleetState` & `updateRoute`) with Validations

Scaffold the MCP tools inside `com.astra.engine.mcp`.

**Files:**
- Create: `backend/src/main/java/com/astra/engine/mcp/McpTools.java`
- Create: `backend/src/main/java/com/astra/engine/mcp/dto/FleetStateRequest.java`
- Create: `backend/src/main/java/com/astra/engine/mcp/dto/RouteUpdateRequest.java`

- [ ] **Step 1: Create request DTOs with validation rules**
`FleetStateRequest.java`:
```java
package com.astra.engine.mcp.dto;

public record FleetStateRequest(String vehicleId) {}
```

`RouteUpdateRequest.java`:
```java
package com.astra.engine.mcp.dto;

import java.util.List;

public record RouteUpdateRequest(
        String vehicleId,
        String routeId,
        List<List<Double>> coordinates,
        String rationale
) {}
```

- [ ] **Step 2: Implement `McpTools.java`**
Enforce NFR 2.1: Route strictly through `TelemetryService` and `RoutingService`. No raw SQL strings or parameters allowed.
```java
package com.astra.engine.mcp;

import com.astra.engine.mcp.dto.FleetStateRequest;
import com.astra.engine.mcp.dto.RouteUpdateRequest;
import com.astra.engine.routing.RouteUpdateResult;
import com.astra.engine.routing.RoutingService;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.VehicleStatusDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class McpTools {

    private final TelemetryService telemetryService;
    private final RoutingService routingService;
    private final ObjectMapper objectMapper;

    public McpTools(TelemetryService telemetryService,
                    RoutingService routingService,
                    ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.routingService = routingService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "getFleetState", description = "Reads current vehicle telemetry and operational status for active delivery vehicles in the Klang Valley.")
    public String getFleetState(
            @ToolParam(description = "Optional vehicle ID (e.g. V-001) to filter for a single vehicle. If omitted, returns all fleet vehicles.", required = false)
            String vehicleId) {
        try {
            List<VehicleStatusDto> fleet = telemetryService.getFleetState(vehicleId);
            return objectMapper.writeValueAsString(Map.of(
                    "status", "SUCCESS",
                    "count", fleet.size(),
                    "vehicles", fleet
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch fleet state: " + e.getMessage(), e);
        }
    }

    @Tool(name = "updateRoute", description = "Writes and commits new detour geometries to a specific vehicle following autonomous negotiation. All coordinates must be in [longitude, latitude] order within the Klang Valley bounding box.")
    public String updateRoute(
            @ToolParam(description = "Unique identifier of the vehicle (e.g. V-001)", required = true)
            String vehicleId,
            @ToolParam(description = "Identifier of the route to update (e.g. RT-001)", required = true)
            String routeId,
            @ToolParam(description = "Ordered list of [longitude, latitude] coordinate pairs representing the new route LineString", required = true)
            List<List<Double>> coordinates,
            @ToolParam(description = "Optional negotiation rationale or detour explanation", required = false)
            String rationale) {

        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required and cannot be blank");
        }
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required and cannot be blank");
        }
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("coordinates must contain at least 2 points");
        }

        try {
            RouteUpdateResult result = routingService.updateRoute(
                    vehicleId.trim(),
                    routeId.trim(),
                    coordinates,
                    rationale != null ? rationale.trim() : null
            );
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update route: " + e.getMessage(), e);
        }
    }
}
```

---

### Task 7: Configure MCP Server Bean with HTTP/SSE Transport

Configure the Spring AI MCP Server beans and application properties.

**Files:**
- Create: `backend/src/main/java/com/astra/engine/mcp/McpServerConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/astra/engine/common/CorsConfig.java`

- [ ] **Step 1: Configure application properties in `application.yml`**
Add MCP server properties:
```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: astra-mcp-server
        version: 1.0.0
        type: SYNC
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
```

- [ ] **Step 2: Update `CorsConfig.java` to permit MCP endpoints**
Add CORS mappings for `/sse` and `/mcp/**`:
```java
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/sse")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*");

        registry.addMapping("/mcp/**")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*");
    }
```

- [ ] **Step 3: Create `McpServerConfig.java`**
Explicitly expose tool callback provider and customize server capabilities:
```java
package com.astra.engine.mcp;

import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(McpTools mcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools)
                .build();
    }

    @Bean
    public McpSyncServerCustomizer mcpSyncServerCustomizer() {
        return spec -> {
            // Additional runtime customizers or info metadata if needed
        };
    }
}
```

---

### Task 8: Scaffold and Run JUnit 5 Integration & Validation Tests

**Files:**
- Create: `backend/src/test/java/com/astra/engine/mcp/McpServerIntegrationTest.java`
- Create: `backend/src/test/java/com/astra/engine/mcp/McpToolsTest.java`

- [ ] **Step 1: Write `McpToolsTest.java` testing both tools and security guardrails**
Unit test `McpTools` directly with mocked services:
1. `getFleetState_withoutVehicleId_returnsAllVehicles`: Verifies complete fleet retrieval.
2. `getFleetState_withVehicleId_returnsFilteredVehicle`: Verifies specific vehicle query.
3. `updateRoute_withValidKlangValleyCoordinates_returnsCommitted`: Verifies valid coordinates (`[[101.6839, 3.1390], [101.5540, 3.0755]]`) return committed status.
4. `updateRoute_withOutOfBoundsCoordinates_throwsIllegalArgumentException`: Verifies coordinates outside Klang Valley (`[105.0, 5.0]`) throw `IllegalArgumentException`.
5. `updateRoute_withLessThanTwoCoordinates_throwsIllegalArgumentException`: Verifies 1-point coordinate array is rejected.
6. `updateRoute_withBlankVehicleId_throwsIllegalArgumentException`: Verifies empty vehicleId rejected.
7. `updateRoute_securityNfr21_rawSqlInputTreatedAsLiteral`: Verifies passing SQL injection string `'; DROP TABLE route; --` in `rationale` or `vehicleId` is treated strictly as a string literal and does NOT execute raw SQL.

- [ ] **Step 2: Write `McpServerIntegrationTest.java` testing HTTP/SSE endpoints**
Use `@SpringBootTest` + `@AutoConfigureMockMvc`:
1. `sseEndpoint_shouldReturnStream`: Perform `GET /sse` and verify HTTP 200 OK with `text/event-stream` content type.
2. `mcpTools_shouldBeRegisteredInContext`: Verify `McpTools` bean and `ToolCallbackProvider` bean are present in the Spring application context with `getFleetState` and `updateRoute`.

- [ ] **Step 3: Run the new MCP tests**
```bash
cd /home/jordan/sunway/capstone/astra/backend
./mvnw test -Dtest=McpToolsTest
./mvnw test -Dtest=McpServerIntegrationTest
```
Expected: All tests pass (green).

---

### Task 9: Full Backend Verification and Git Commit Preparation

- [ ] **Step 1: Run the full backend test suite**
```bash
cd /home/jordan/sunway/capstone/astra/backend
./mvnw test
```
Expected: All unit and integration tests pass without failures.

- [ ] **Step 2: Inspect git diff and verify secret hygiene**
```bash
git status
git diff
```
Ensure `frontend/src/environments/environment.ts` is NOT modified or committed.

- [ ] **Step 3: Create atomic git commits**
1. `git add backend/pom.xml backend/src/main/resources/application.yml`
   `git commit -m "feat(mcp): add Spring AI MCP WebMVC starter dependencies and configuration"`
2. `git add backend/src/main/java/com/astra/engine/common/GeometryUtils.java backend/src/test/java/com/astra/engine/common/GeometryUtilsTest.java`
   `git commit -m "feat(common): add LineString construction and Klang Valley bounding validation"`
3. `git add backend/src/main/java/com/astra/engine/routing/Route.java backend/src/main/java/com/astra/engine/routing/RouteRepository.java backend/src/main/java/com/astra/engine/routing/RouteUpdateResult.java backend/src/main/java/com/astra/engine/routing/RoutingService.java backend/src/main/java/com/astra/engine/telemetry/VehicleStatusDto.java backend/src/main/java/com/astra/engine/telemetry/VehicleTelemetryRepository.java backend/src/main/java/com/astra/engine/telemetry/TelemetryService.java`
   `git commit -m "feat(routing): introduce Route entity and domain methods for fleet state and route update"`
4. `git add backend/src/main/java/com/astra/engine/mcp/ backend/src/main/java/com/astra/engine/common/CorsConfig.java backend/src/test/java/com/astra/engine/mcp/`
   `git commit -m "feat(mcp): implement Java MCP Server with getFleetState and updateRoute tools"`

---

## Plan Self-Review Checklist

- [x] **Branching:** Dedicated `feature/13-java-mcp-server` branch.
- [x] **Dependencies:** Official `spring-ai-starter-mcp-server-webmvc` 2.0.2 with `spring-ai-bom`.
- [x] **Transport:** HTTP/SSE transport (`/sse`, `/mcp/message`) configured on Spring Boot port `8081`.
- [x] **Tools Registered:** `getFleetState` and `updateRoute` with full JSON Schema descriptions.
- [x] **Security Guardrail (NFR 2.1):** Zero raw SQL. All data access routed strictly through `TelemetryService` and `RoutingService` using Spring Data JPA.
- [x] **Spatial Standard:** WGS 84 (`SRID 4326`), `[longitude, latitude]` ordering, Klang Valley bounding box (`Lat: [2.90, 3.30]`, `Lng: [101.40, 101.90]`).
- [x] **Tests Scaffolding:** Comprehensive JUnit 5 unit and integration tests covering tool registration, execution, and invalid argument rejection.
