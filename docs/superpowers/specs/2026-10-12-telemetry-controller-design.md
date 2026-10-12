# Design Spec: Telemetry Ingestion REST API Controller & Service

## 1. Overview
Sprint 1 Task 5 for the Astra project. Implement the initial Spring Boot REST Controller exposing `POST /api/v1/telemetry` (and alias `/api/telemetry`) to ingest high-frequency simulated GPS coordinates and movement vectors, validate Klang Valley bounding box boundaries, persist spatial records to PostGIS via `VehicleTelemetryRepository`, and return standardized DTO responses per `04-api-contracts.md`.

## 2. Technical Requirements
- Dependency: Add `spring-boot-starter-validation` (BOM-managed) to `backend/pom.xml`.
- Request Mapping: Support both `POST /api/v1/telemetry` and `POST /api/telemetry` via `@RequestMapping({"/api/v1/telemetry", "/api/telemetry"})`.
- DTO records in `com.astra.engine.telemetry`:
  - `TelemetryIngestRequest`: `vehicleId` (not blank), `latitude` (not null, 2.90 to 3.30), `longitude` (not null, 101.40 to 101.90), `bearing` (optional double), `speedKmh` (optional double), `routeId` (optional string).
  - `TelemetryIngestResponse`: `vehicleId`, `status` ("NOMINAL"), `intersectedAnomalyId` (null in Sprint 1), `updatedAt` (Instant).
- Service Layer: `TelemetryService` in `com.astra.engine.telemetry`:
  - Creates JTS Point via `GeometryUtils.createPoint(request.longitude(), request.latitude())` enforcing SRID 4326 and `(x = lng, y = lat)`.
  - Persists `VehicleTelemetry` entity using `VehicleTelemetryRepository`.
  - Returns `TelemetryIngestResponse`.
- Controller: `TelemetryController` in `com.astra.engine.telemetry`:
  - `@RestController`, `@PostMapping` accepting `@Valid @RequestBody TelemetryIngestRequest`.
  - Returns `ResponseEntity.ok(response)`.
- Global Exception Handler: `GlobalExceptionHandler` in `com.astra.engine.common`:
  - Handles `MethodArgumentNotValidException` and returns HTTP 400 Bad Request with descriptive validation errors.
- Guardrails:
  - Technical Guardrail 6.A: Coordinates strictly validated within Klang Valley (`Lat: 2.90–3.30`, `Lng: 101.40–101.90`), Point created as `(longitude, latitude)`.
  - Technical Guardrail 6.B: Never return JPA entities or JTS geometries directly from the controller; strictly return Java records.

## 3. Architecture & Components

### 3.1 DTOs (`com.astra.engine.telemetry`)
```java
public record TelemetryIngestRequest(
    @NotBlank(message = "vehicleId is required")
    String vehicleId,

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "2.90", message = "latitude must be within Klang Valley (>= 2.90)")
    @DecimalMax(value = "3.30", message = "latitude must be within Klang Valley (<= 3.30)")
    Double latitude,

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "101.40", message = "longitude must be within Klang Valley (>= 101.40)")
    @DecimalMax(value = "101.90", message = "longitude must be within Klang Valley (<= 101.90)")
    Double longitude,

    Double bearing,
    Double speedKmh,
    String routeId
) {}

public record TelemetryIngestResponse(
    String vehicleId,
    String status,
    String intersectedAnomalyId,
    Instant updatedAt
) {}
```

### 3.2 Service (`com.astra.engine.telemetry.TelemetryService`)
```java
@Service
public class TelemetryService {
    private final VehicleTelemetryRepository repository;

    public TelemetryService(VehicleTelemetryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TelemetryIngestResponse ingest(TelemetryIngestRequest request) {
        Point point = GeometryUtils.createPoint(request.longitude(), request.latitude());
        VehicleTelemetry entity = new VehicleTelemetry(
            request.vehicleId(),
            request.routeId(),
            point,
            request.bearing(),
            request.speedKmh(),
            Instant.now()
        );
        repository.save(entity);
        return new TelemetryIngestResponse(
            request.vehicleId(),
            "NOMINAL",
            null,
            entity.getRecordedAt()
        );
    }
}
```

### 3.3 Controller (`com.astra.engine.telemetry.TelemetryController`)
```java
@RestController
@RequestMapping({"/api/v1/telemetry", "/api/telemetry"})
public class TelemetryController {
    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<TelemetryIngestResponse> ingestTelemetry(@Valid @RequestBody TelemetryIngestRequest request) {
        return ResponseEntity.ok(telemetryService.ingest(request));
    }
}
```

## 4. Verification Criteria
1. Unit/WebMvc test `TelemetryControllerTest`:
   - Valid request to `/api/v1/telemetry` returns 200 with matching response JSON.
   - Valid request to `/api/telemetry` returns 200 with matching response JSON.
   - Missing vehicleId returns 400.
   - Latitude out of Klang Valley bounds (< 2.90 or > 3.30) returns 400.
   - Longitude out of Klang Valley bounds (< 101.40 or > 101.90) returns 400.
2. Integration test `TelemetryIntegrationTest`:
   - POST valid payload to `/api/v1/telemetry`, verify HTTP 200 response and assert entity is saved in containerized PostGIS `telemetry_log` table with SRID 4326.
3. Full `./mvnw test` passes with zero failures.
