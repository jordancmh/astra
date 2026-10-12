# Telemetry REST Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Spring Boot REST controller exposing `POST /api/v1/telemetry` (and alias `/api/telemetry`) to ingest simulated GPS coordinates and movement vectors, validate Klang Valley bounding box boundaries, persist spatial records to PostGIS via `TelemetryService` and `VehicleTelemetryRepository`, and return standardized DTO responses.

**Architecture:** Spring Web controller accepting JSON request DTOs with Jakarta Bean Validation. `TelemetryService` constructs SRID 4326 JTS Points using `GeometryUtils` and saves to `telemetry_log` via `VehicleTelemetryRepository`. Responses are clean Java records. `GlobalExceptionHandler` formats validation failures as HTTP 400.

**Tech Stack:** Java 21, Spring Boot 4.0.8, Spring Web, Spring Data JPA, Jakarta Validation, Hibernate Spatial, JTS Core, PostGIS.

## Global Constraints
- Controller package: `com.astra.engine.telemetry`
- Service package: `com.astra.engine.telemetry`
- Exception handler package: `com.astra.engine.common`
- Endpoints: `@RequestMapping({"/api/v1/telemetry", "/api/telemetry"})`
- Klang Valley bounds: Latitude between `2.90` and `3.30`, Longitude between `101.40` and `101.90`
- Response status: `"NOMINAL"` with `intersectedAnomalyId: null`
- JTS Point created as `(longitude, latitude)` with SRID 4326
- Never return raw entities or JTS geometries directly from controller

---

### Task 1: Add `spring-boot-starter-validation` & Implement DTO Records

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/astra/engine/telemetry/TelemetryIngestRequest.java`
- Create: `backend/src/main/java/com/astra/engine/telemetry/TelemetryIngestResponse.java`
- Create: `backend/src/main/java/com/astra/engine/common/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `TelemetryIngestRequest` record with Bean Validation constraints for Klang Valley bounding box (`2.90 <= lat <= 3.30`, `101.40 <= lng <= 101.90`, non-blank `vehicleId`).
- Produces: `TelemetryIngestResponse` record with `vehicleId`, `status`, `intersectedAnomalyId`, and `updatedAt`.
- Produces: `GlobalExceptionHandler` returning HTTP 400 for validation errors.

- [ ] **Step 1: Add `spring-boot-starter-validation` to `backend/pom.xml`**

In `backend/pom.xml`, add dependency:
```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
```

- [ ] **Step 2: Create `TelemetryIngestRequest.java`**

Create `backend/src/main/java/com/astra/engine/telemetry/TelemetryIngestRequest.java`:
```java
package com.astra.engine.telemetry;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
) {
}
```

- [ ] **Step 3: Create `TelemetryIngestResponse.java`**

Create `backend/src/main/java/com/astra/engine/telemetry/TelemetryIngestResponse.java`:
```java
package com.astra.engine.telemetry;

import java.time.Instant;

public record TelemetryIngestResponse(
        String vehicleId,
        String status,
        String intersectedAnomalyId,
        Instant updatedAt
) {
}
```

- [ ] **Step 4: Create `GlobalExceptionHandler.java`**

Create `backend/src/main/java/com/astra/engine/common/GlobalExceptionHandler.java`:
```java
package com.astra.engine.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("errors", errors);

        return ResponseEntity.badRequest().body(body);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd backend && ./mvnw compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit DTOs and validation**

```bash
git add backend/pom.xml backend/src/main/java/com/astra/engine/telemetry/TelemetryIngestRequest.java backend/src/main/java/com/astra/engine/telemetry/TelemetryIngestResponse.java backend/src/main/java/com/astra/engine/common/GlobalExceptionHandler.java
git commit -m "feat(telemetry): add validation starter, DTO records, and global exception handler"
```

---

### Task 2: Implement `TelemetryService` and `TelemetryController` with Unit & Integration Tests

**Files:**
- Create: `backend/src/main/java/com/astra/engine/telemetry/TelemetryService.java`
- Create: `backend/src/main/java/com/astra/engine/telemetry/TelemetryController.java`
- Create: `backend/src/test/java/com/astra/engine/telemetry/TelemetryControllerTest.java`

**Interfaces:**
- Consumes: `TelemetryIngestRequest`, `VehicleTelemetryRepository`, `GeometryUtils`
- Produces: `POST /api/v1/telemetry` and `POST /api/telemetry` endpoints returning `TelemetryIngestResponse`.

- [ ] **Step 1: Write controller unit tests in `TelemetryControllerTest.java`**

Create `backend/src/test/java/com/astra/engine/telemetry/TelemetryControllerTest.java`:
```java
package com.astra.engine.telemetry;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TelemetryService telemetryService;

    @Test
    void ingest_shouldReturn200AndResponse_whenPayloadIsValid() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 3.1140, 101.6371, 87.0, 78.0, "RT-001"
        );
        TelemetryIngestResponse response = new TelemetryIngestResponse(
                "V-001", "NOMINAL", null, Instant.parse("2026-10-12T08:30:00Z")
        );
        when(telemetryService.ingest(any(TelemetryIngestRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V-001"))
                .andExpect(jsonPath("$.status").value("NOMINAL"))
                .andExpect(jsonPath("$.intersectedAnomalyId").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").value("2026-10-12T08:30:00Z"));
    }

    @Test
    void ingest_shouldAcceptAliasEndpoint() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 3.1140, 101.6371, 87.0, 78.0, "RT-001"
        );
        TelemetryIngestResponse response = new TelemetryIngestResponse(
                "V-001", "NOMINAL", null, Instant.parse("2026-10-12T08:30:00Z")
        );
        when(telemetryService.ingest(any(TelemetryIngestRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V-001"));
    }

    @Test
    void ingest_shouldReturn400_whenLatitudeIsOutOfKlangValleyBounds() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 5.0000, 101.6371, 87.0, 78.0, "RT-001"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    void ingest_shouldReturn400_whenLongitudeIsOutOfKlangValleyBounds() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-001", 3.1140, 100.0000, 87.0, 78.0, "RT-001"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.longitude").exists());
    }

    @Test
    void ingest_shouldReturn400_whenVehicleIdIsBlank() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "", 3.1140, 101.6371, 87.0, 78.0, "RT-001"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vehicleId").exists());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=TelemetryControllerTest`
Expected: Compilation failure (cannot find symbol `TelemetryController` / `TelemetryService`).

- [ ] **Step 3: Implement `TelemetryService.java`**

Create `backend/src/main/java/com/astra/engine/telemetry/TelemetryService.java`:
```java
package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TelemetryService {

    private final VehicleTelemetryRepository telemetryRepository;

    public TelemetryService(VehicleTelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    @Transactional
    public TelemetryIngestResponse ingest(TelemetryIngestRequest request) {
        Point coordinates = GeometryUtils.createPoint(request.longitude(), request.latitude());
        VehicleTelemetry entity = new VehicleTelemetry(
                request.vehicleId(),
                request.routeId(),
                coordinates,
                request.bearing(),
                request.speedKmh(),
                Instant.now()
        );
        VehicleTelemetry saved = telemetryRepository.save(entity);

        return new TelemetryIngestResponse(
                saved.getVehicleId(),
                "NOMINAL",
                null,
                saved.getRecordedAt()
        );
    }
}
```

- [ ] **Step 4: Implement `TelemetryController.java`**

Create `backend/src/main/java/com/astra/engine/telemetry/TelemetryController.java`:
```java
package com.astra.engine.telemetry;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/telemetry", "/api/telemetry"})
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<TelemetryIngestResponse> ingestTelemetry(
            @Valid @RequestBody TelemetryIngestRequest request) {
        TelemetryIngestResponse response = telemetryService.ingest(request);
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 5: Run controller unit tests**

Run: `cd backend && ./mvnw test -Dtest=TelemetryControllerTest`
Expected: `BUILD SUCCESS` (5 tests pass).

- [ ] **Step 6: Write end-to-end integration test `TelemetryIntegrationTest.java`**

Create `backend/src/test/java/com/astra/engine/telemetry/TelemetryIntegrationTest.java`:
```java
package com.astra.engine.telemetry;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelemetryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleTelemetryRepository repository;

    @Test
    void ingestTelemetry_shouldPersistEntityToPostGisAndReturn200() throws Exception {
        TelemetryIngestRequest request = new TelemetryIngestRequest(
                "V-TEST-99", 3.1140, 101.6371, 90.0, 60.0, "RT-100"
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V-TEST-99"))
                .andExpect(jsonPath("$.status").value("NOMINAL"))
                .andExpect(jsonPath("$.updatedAt").exists());

        List<VehicleTelemetry> logs = repository.findByVehicleIdOrderByRecordedAtDesc("V-TEST-99");
        assertThat(logs).isNotEmpty();
        VehicleTelemetry saved = logs.get(0);
        assertThat(saved.getVehicleId()).isEqualTo("V-TEST-99");
        assertThat(saved.getRouteId()).isEqualTo("RT-100");
        assertThat(saved.getCoordinates().getSRID()).isEqualTo(4326);
        assertThat(saved.getCoordinates().getX()).isEqualTo(101.6371);
        assertThat(saved.getCoordinates().getY()).isEqualTo(3.1140);
        assertThat(saved.getSpeedKmh()).isEqualTo(60.0);
        assertThat(saved.getBearing()).isEqualTo(90.0);
    }
}
```

- [ ] **Step 7: Run full test suite**

Run: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS` (all tests pass, 0 failures, 0 errors).

- [ ] **Step 8: Commit controller, service, and tests**

```bash
git add backend/src/main/java/com/astra/engine/telemetry/TelemetryService.java backend/src/main/java/com/astra/engine/telemetry/TelemetryController.java backend/src/test/java/com/astra/engine/telemetry/TelemetryControllerTest.java backend/src/test/java/com/astra/engine/telemetry/TelemetryIntegrationTest.java
git commit -m "feat(telemetry): implement telemetry ingestion controller, service, and integration tests"
```
