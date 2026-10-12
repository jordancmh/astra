# Design Spec: VehicleTelemetry JPA Entity & Spatial Mapping

## 1. Overview
Sprint 1 Task 4 for the Astra project. Create the `VehicleTelemetry` JPA entity mapped to the `telemetry_log` PostGIS table, provide a spatial utility for strict SRID 4326 `(Longitude, Latitude)` point creation, and implement a Spring Data JPA repository with integration test verification.

## 2. Technical Requirements
- Map JPA Entity `VehicleTelemetry` to table `telemetry_log`.
- Primary key: `UUID id` using `@GeneratedValue(strategy = GenerationType.UUID)`.
- Vehicle reference: `String vehicleId` mapped to `vehicle_id` (nullable = false).
- Optional route reference: `String routeId` mapped to `route_id` (to align with `04-api-contracts.md` POST `/api/v1/telemetry` payload).
- Spatial field: `org.locationtech.jts.geom.Point coordinates` annotated with `@Column(name = "coordinates", columnDefinition = "geometry(Point, 4326)", nullable = false)`.
- Movement vectors: `Double bearing` and `Double speedKmh` mapped to `speed_kmh`.
- Timestamp: `Instant recordedAt` with a `@PrePersist` hook that defaults `recordedAt = Instant.now()` if null.
- Provide `GeometryUtils` in `com.astra.engine.common` with a singleton `GeometryFactory` enforcing SRID 4326 and `(x = longitude, y = latitude)` order.
- Implement `VehicleTelemetryRepository` in `com.astra.engine.telemetry`.
- Integration test in `VehicleTelemetryRepositoryTest` connecting to live PostGIS container via `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` or `@SpringBootTest`.

## 3. Architecture & Entities

### 3.1 `VehicleTelemetry` (`com.astra.engine.telemetry`)
```java
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

    @PrePersist
    public void prePersist() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }
    // Constructors, getters, setters
}
```

### 3.2 `GeometryUtils` (`com.astra.engine.common`)
```java
public final class GeometryUtils {
    public static final int SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), SRID);

    public static Point createPoint(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
```

### 3.3 `VehicleTelemetryRepository` (`com.astra.engine.telemetry`)
```java
public interface VehicleTelemetryRepository extends JpaRepository<VehicleTelemetry, UUID> {
    List<VehicleTelemetry> findByVehicleIdOrderByRecordedAtDesc(String vehicleId);
}
```

## 4. Verification Criteria
1. Unit test `GeometryUtilsTest` asserts `point.getSRID() == 4326`, `point.getX() == longitude`, `point.getY() == latitude`.
2. Integration test `VehicleTelemetryRepositoryTest` persists a `VehicleTelemetry` record to containerized PostGIS and retrieves it, verifying all fields and spatial point properties.
3. Tests run via `cd backend && ./mvnw test` with 0 failures.
