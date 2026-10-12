# VehicleTelemetry JPA Entity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `VehicleTelemetry` JPA entity, spatial utility `GeometryUtils`, and Spring Data repository `VehicleTelemetryRepository`, verified with unit and integration tests against the containerized PostGIS database.

**Architecture:** The `VehicleTelemetry` entity maps to the `telemetry_log` PostGIS table in PostgreSQL. Spatial points use JTS Core `Point` with SRID 4326 and `[longitude, latitude]` ordering encapsulated by `GeometryUtils`. Spring Data JPA handles persistence and query retrieval.

**Tech Stack:** Java 21, Spring Boot 4.0.8, Spring Data JPA, Hibernate Spatial, JTS Core, PostgreSQL 16 + PostGIS 3.4.

## Global Constraints
- Package for entity & repo: `com.astra.engine.telemetry`
- Package for spatial utils: `com.astra.engine.common`
- Table name: `telemetry_log`
- Spatial column definition: `@Column(name = "coordinates", columnDefinition = "geometry(Point, 4326)", nullable = false)`
- Coordinate ordering: `(x, y) = (longitude, latitude)` strictly enforced
- SRID: 4326 strictly enforced
- `recordedAt`: Defaults to `Instant.now()` via `@PrePersist` if null
- `routeId`: Optional column `route_id` included

---

### Task 1: Create `GeometryUtils` with Strict SRID 4326 and Longitude-Latitude Ordering

**Files:**
- Create: `backend/src/main/java/com/astra/engine/common/GeometryUtils.java`
- Create: `backend/src/test/java/com/astra/engine/common/GeometryUtilsTest.java`

**Interfaces:**
- Produces: `GeometryUtils.createPoint(double longitude, double latitude) -> Point` with SRID 4326 and `x=longitude, y=latitude`.

- [ ] **Step 1: Write unit test in `GeometryUtilsTest.java`**

```java
package com.astra.engine.common;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryUtilsTest {

    @Test
    void createPoint_shouldEnforceSrid4326AndCoordinateOrder() {
        double longitude = 101.6371;
        double latitude = 3.1140;

        Point point = GeometryUtils.createPoint(longitude, latitude);

        assertThat(point).isNotNull();
        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.getX()).isEqualTo(longitude);
        assertThat(point.getY()).isEqualTo(latitude);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GeometryUtilsTest`
Expected: Compilation failure (cannot find symbol `GeometryUtils`).

- [ ] **Step 3: Implement `GeometryUtils.java`**

```java
package com.astra.engine.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeometryUtils {

    public static final int SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID);

    private GeometryUtils() {
    }

    public static Point createPoint(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GeometryUtilsTest`
Expected: `BUILD SUCCESS` (1 test passed).

- [ ] **Step 5: Commit changes**

```bash
git add backend/src/main/java/com/astra/engine/common/GeometryUtils.java backend/src/test/java/com/astra/engine/common/GeometryUtilsTest.java
git commit -m "feat(common): add GeometryUtils for strict SRID 4326 point creation"
```

---

### Task 2: Implement `VehicleTelemetry` JPA Entity & `VehicleTelemetryRepository`

**Files:**
- Create: `backend/src/main/java/com/astra/engine/telemetry/VehicleTelemetry.java`
- Create: `backend/src/main/java/com/astra/engine/telemetry/VehicleTelemetryRepository.java`
- Create: `backend/src/test/java/com/astra/engine/telemetry/VehicleTelemetryRepositoryTest.java`

**Interfaces:**
- Consumes: `GeometryUtils.createPoint(double longitude, double latitude)`
- Produces: `VehicleTelemetry` entity and `VehicleTelemetryRepository` repository.

- [ ] **Step 1: Write failing integration test in `VehicleTelemetryRepositoryTest.java`**

```java
package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VehicleTelemetryRepositoryTest {

    @Autowired
    private VehicleTelemetryRepository repository;

    @Test
    void saveAndFindById_shouldPersistAndRetrieveTelemetryWithPostGisPoint() {
        double lng = 101.6371;
        double lat = 3.1140;
        Point point = GeometryUtils.createPoint(lng, lat);

        VehicleTelemetry telemetry = new VehicleTelemetry();
        telemetry.setVehicleId("V-001");
        telemetry.setRouteId("RT-001");
        telemetry.setCoordinates(point);
        telemetry.setBearing(87.0);
        telemetry.setSpeedKmh(78.0);
        // recordedAt is null to test @PrePersist

        VehicleTelemetry saved = repository.save(telemetry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecordedAt()).isNotNull();

        Optional<VehicleTelemetry> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getVehicleId()).isEqualTo("V-001");
        assertThat(found.get().getRouteId()).isEqualTo("RT-001");
        assertThat(found.get().getCoordinates().getSRID()).isEqualTo(4326);
        assertThat(found.get().getCoordinates().getX()).isEqualTo(lng);
        assertThat(found.get().getCoordinates().getY()).isEqualTo(lat);
        assertThat(found.get().getBearing()).isEqualTo(87.0);
        assertThat(found.get().getSpeedKmh()).isEqualTo(78.0);
    }

    @Test
    void findByVehicleIdOrderByRecordedAtDesc_shouldReturnSortedList() {
        Point point = GeometryUtils.createPoint(101.6371, 3.1140);

        VehicleTelemetry t1 = new VehicleTelemetry();
        t1.setVehicleId("V-002");
        t1.setCoordinates(point);
        t1.setRecordedAt(Instant.now().minusSeconds(60));
        repository.save(t1);

        VehicleTelemetry t2 = new VehicleTelemetry();
        t2.setVehicleId("V-002");
        t2.setCoordinates(point);
        t2.setRecordedAt(Instant.now());
        repository.save(t2);

        List<VehicleTelemetry> logs = repository.findByVehicleIdOrderByRecordedAtDesc("V-002");
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getRecordedAt()).isAfter(logs.get(1).getRecordedAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=VehicleTelemetryRepositoryTest`
Expected: Compilation failure (cannot find symbol `VehicleTelemetry` / `VehicleTelemetryRepository`).

- [ ] **Step 3: Implement `VehicleTelemetry.java`**

```java
package com.astra.engine.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_log")
public class VehicleTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(name = "route_id")
    private String routeId;

    @Column(name = "coordinates", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point coordinates;

    @Column(name = "bearing")
    private Double bearing;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public VehicleTelemetry() {
    }

    public VehicleTelemetry(String vehicleId, String routeId, Point coordinates, Double bearing, Double speedKmh, Instant recordedAt) {
        this.vehicleId = vehicleId;
        this.routeId = routeId;
        this.coordinates = coordinates;
        this.bearing = bearing;
        this.speedKmh = speedKmh;
        this.recordedAt = recordedAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public Point getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Point coordinates) {
        this.coordinates = coordinates;
    }

    public Double getBearing() {
        return bearing;
    }

    public void setBearing(Double bearing) {
        this.bearing = bearing;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
```

- [ ] **Step 4: Implement `VehicleTelemetryRepository.java`**

```java
package com.astra.engine.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehicleTelemetryRepository extends JpaRepository<VehicleTelemetry, UUID> {

    List<VehicleTelemetry> findByVehicleIdOrderByRecordedAtDesc(String vehicleId);
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=VehicleTelemetryRepositoryTest`
Expected: `BUILD SUCCESS` (2 tests passed).

- [ ] **Step 6: Run full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS` (all tests passed, including `AstraEngineApplicationTests` and `GeometryUtilsTest`).

- [ ] **Step 7: Commit changes**

```bash
git add backend/src/main/java/com/astra/engine/telemetry/VehicleTelemetry.java backend/src/main/java/com/astra/engine/telemetry/VehicleTelemetryRepository.java backend/src/test/java/com/astra/engine/telemetry/VehicleTelemetryRepositoryTest.java
git commit -m "feat(telemetry): implement VehicleTelemetry JPA entity and repository"
```
