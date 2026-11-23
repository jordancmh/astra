package com.astra.engine.simulation;

import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.GraphHopperClient;
import com.astra.engine.routing.GraphHopperRouteResponse;
import com.astra.engine.routing.Route;
import com.astra.engine.routing.RouteRepository;
import com.astra.engine.simulation.dto.SimulationStatusResponse;
import com.astra.engine.telemetry.TelemetryIngestRequest;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.Vehicle;
import com.astra.engine.telemetry.VehicleRepository;
import jakarta.annotation.PreDestroy;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final TelemetryService telemetryService;
    private final GraphHopperClient graphHopperClient;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tickerFuture;
    private volatile boolean isRunning = false;
    private final AtomicLong tickCount = new AtomicLong(0);

    // Track vehicle movement index and last known route geometry string
    private final Map<String, Integer> vehicleIndices = new ConcurrentHashMap<>();
    private final Map<String, String> vehicleRouteGeomSignatures = new ConcurrentHashMap<>();

    public record SeedVehicleSpec(
            String vehicleId,
            String routeId,
            int cargoPriority,
            List<Double> origin,      // [lng, lat]
            List<Double> waypoint,    // [lng, lat] (Issue #18 corridor preset)
            List<Double> destination  // [lng, lat]
    ) {}

    public static final List<SeedVehicleSpec> SEED_SPECS = List.of(
            // V-001: P5 Critical Pharma — Federal Highway
            new SeedVehicleSpec("V-001", "RT-001", 5,
                    List.of(101.5270, 3.0730), List.of(101.5833, 3.0833), List.of(101.7116, 3.1579)),
            // V-002: P4 Cold Chain Foodstuffs — LDP
            new SeedVehicleSpec("V-002", "RT-002", 4,
                    List.of(101.6150, 3.1480), List.of(101.6035, 3.1085), List.of(101.6210, 3.0250)),
            // V-003: P3 Express Logistics — NPE
            new SeedVehicleSpec("V-003", "RT-003", 3,
                    List.of(101.6060, 3.0710), List.of(101.6620, 3.1180), List.of(101.6680, 3.1280)),
            // V-004: P2 Standard Retail Restock — KLCC Core
            new SeedVehicleSpec("V-004", "RT-004", 2,
                    List.of(101.7600, 3.1490), List.of(101.7116, 3.1579), List.of(101.6860, 3.1340)),
            // V-005: P1 Standard Dry Goods — Federal Highway (Contending pair with V-001)
            new SeedVehicleSpec("V-005", "RT-005", 1,
                    List.of(101.5560, 3.1300), List.of(101.5833, 3.0833), List.of(101.6760, 3.1180))
    );

    public SimulationService(VehicleRepository vehicleRepository,
                             RouteRepository routeRepository,
                             TelemetryService telemetryService,
                             GraphHopperClient graphHopperClient) {
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
        this.telemetryService = telemetryService;
        this.graphHopperClient = graphHopperClient;
    }

    @Transactional
    public SimulationStatusResponse seedFleet() {
        log.info("[SIMULATION] Seeding {} Klang Valley demo vehicles across corridors...", SEED_SPECS.size());

        for (SeedVehicleSpec spec : SEED_SPECS) {
            List<List<Double>> waypoints = List.of(spec.origin(), spec.waypoint(), spec.destination());
            List<List<Double>> pathCoordinates;
            double distanceMeters;
            long timeMs;

            try {
                GraphHopperRouteResponse.GraphHopperPath ghPath = graphHopperClient.routeWithWaypoints(waypoints);
                if (ghPath != null && ghPath.points() != null && ghPath.points().coordinates() != null) {
                    pathCoordinates = ghPath.points().coordinates();
                    distanceMeters = ghPath.distance();
                    timeMs = ghPath.time();
                } else {
                    pathCoordinates = generateFallbackPolyline(spec.origin(), spec.waypoint(), spec.destination());
                    distanceMeters = 25000.0;
                    timeMs = 1800000L;
                }
            } catch (Exception e) {
                log.warn("[SIMULATION] GraphHopper unavailable for {}, using deterministic corridor fallback: {}",
                        spec.routeId(), e.getMessage());
                pathCoordinates = generateFallbackPolyline(spec.origin(), spec.waypoint(), spec.destination());
                distanceMeters = 25000.0;
                timeMs = 1800000L;
            }

            LineString lineString = GeometryUtils.createLineString(pathCoordinates);
            List<Double> startCoord = pathCoordinates.get(0);
            List<Double> nextCoord = pathCoordinates.size() > 1 ? pathCoordinates.get(1) : startCoord;

            Point originPoint = GeometryUtils.createPoint(startCoord.get(0), startCoord.get(1));
            List<Double> endCoord = pathCoordinates.get(pathCoordinates.size() - 1);
            Point destPoint = GeometryUtils.createPoint(endCoord.get(0), endCoord.get(1));

            Route route = new Route(spec.routeId(), "OPTIMAL", originPoint, destPoint, lineString, distanceMeters, timeMs);
            routeRepository.save(route);

            double bearing = GeometryUtils.calculateBearing(startCoord.get(1), startCoord.get(0), nextCoord.get(1), nextCoord.get(0));
            double initialSpeed = 55.0;

            Vehicle vehicle = new Vehicle(
                    spec.vehicleId(),
                    "NOMINAL",
                    spec.routeId(),
                    originPoint,
                    bearing,
                    initialSpeed,
                    spec.cargoPriority()
            );
            vehicleRepository.save(vehicle);

            telemetryService.ingest(new TelemetryIngestRequest(
                    spec.vehicleId(),
                    startCoord.get(1),
                    startCoord.get(0),
                    bearing,
                    initialSpeed,
                    spec.routeId()
            ));

            vehicleIndices.put(spec.vehicleId(), 0);
            vehicleRouteGeomSignatures.put(spec.vehicleId(), lineString.toText());
        }

        return new SimulationStatusResponse(isRunning, SEED_SPECS.size(), tickCount.get(), "Seeded 5 demo vehicles successfully");
    }

    public synchronized SimulationStatusResponse startSimulation() {
        if (isRunning) {
            return getStatus();
        }
        isRunning = true;
        tickerFuture = scheduler.scheduleAtFixedRate(this::tickSafe, 0, 1500, TimeUnit.MILLISECONDS);
        log.info("[SIMULATION] Movement ticker started (1500ms interval).");
        return getStatus();
    }

    public synchronized SimulationStatusResponse stopSimulation() {
        if (!isRunning) {
            return getStatus();
        }
        isRunning = false;
        if (tickerFuture != null) {
            tickerFuture.cancel(false);
            tickerFuture = null;
        }
        log.info("[SIMULATION] Movement ticker stopped.");
        return getStatus();
    }

    @Transactional
    public synchronized SimulationStatusResponse resetSimulation() {
        stopSimulation();
        vehicleIndices.clear();
        vehicleRouteGeomSignatures.clear();
        tickCount.set(0);

        for (SeedVehicleSpec spec : SEED_SPECS) {
            vehicleRepository.deleteById(spec.vehicleId());
            routeRepository.deleteById(spec.routeId());
        }

        log.info("[SIMULATION] Fleet simulation reset. Deleted seeded vehicles and routes.");
        return new SimulationStatusResponse(false, 0, 0, "Simulation reset complete");
    }

    public SimulationStatusResponse getStatus() {
        int count = (int) vehicleRepository.count();
        return new SimulationStatusResponse(isRunning, count, tickCount.get(), isRunning ? "Running" : "Paused");
    }

    public void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            log.error("[SIMULATION] Error during movement tick: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void tick() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        if (vehicles.isEmpty()) {
            return;
        }

        tickCount.incrementAndGet();

        for (Vehicle vehicle : vehicles) {
            String routeId = vehicle.getCurrentRouteId();
            if (routeId == null) {
                continue;
            }

            Route route = routeRepository.findById(routeId).orElse(null);
            if (route == null || route.getPathGeometry() == null) {
                continue;
            }

            Coordinate[] coordinates = route.getPathGeometry().getCoordinates();
            if (coordinates.length < 2) {
                continue;
            }

            String currentGeomSignature = route.getPathGeometry().toText();
            String prevGeomSignature = vehicleRouteGeomSignatures.get(vehicle.getId());

            int currentIndex = vehicleIndices.getOrDefault(vehicle.getId(), 0);

            // Detour Adaptation: If route geometry was updated via MCP detour, adapt current position
            if (prevGeomSignature != null && !prevGeomSignature.equals(currentGeomSignature)) {
                log.info("[SIMULATION] Detected detour route update for vehicle {}. Redirecting along new geometry...", vehicle.getId());
                vehicleRouteGeomSignatures.put(vehicle.getId(), currentGeomSignature);
                currentIndex = findClosestCoordinateIndex(vehicle.getCurrentLocation(), coordinates);
            }

            int nextIndex = (currentIndex + 1) % coordinates.length;
            vehicleIndices.put(vehicle.getId(), nextIndex);

            Coordinate currCoord = coordinates[currentIndex];
            Coordinate nextCoord = coordinates[nextIndex];

            double bearing = GeometryUtils.calculateBearing(currCoord.y, currCoord.x, nextCoord.y, nextCoord.x);
            double speed = 50.0 + (nextIndex % 6) * 3.0; // Realistic dynamic speed 50-68 km/h

            Point newLocation = GeometryUtils.createPoint(nextCoord.x, nextCoord.y);
            vehicle.setCurrentLocation(newLocation);
            vehicle.setBearing(bearing);
            vehicle.setSpeedKmh(speed);
            vehicle.setUpdatedAt(Instant.now());
            vehicleRepository.save(vehicle);

            telemetryService.ingest(new TelemetryIngestRequest(
                    vehicle.getId(),
                    nextCoord.y,
                    nextCoord.x,
                    bearing,
                    speed,
                    routeId
            ));
        }
    }

    private int findClosestCoordinateIndex(Point currentLoc, Coordinate[] coordinates) {
        if (currentLoc == null || coordinates == null || coordinates.length == 0) {
            return 0;
        }
        double minDistance = Double.MAX_VALUE;
        int bestIdx = 0;
        double x = currentLoc.getX();
        double y = currentLoc.getY();

        for (int i = 0; i < coordinates.length; i++) {
            double dx = coordinates[i].x - x;
            double dy = coordinates[i].y - y;
            double distSq = dx * dx + dy * dy;
            if (distSq < minDistance) {
                minDistance = distSq;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private List<List<Double>> generateFallbackPolyline(List<Double> start, List<Double> mid, List<Double> end) {
        List<List<Double>> coords = new ArrayList<>();
        int stepsPerLeg = 10;
        for (int i = 0; i < stepsPerLeg; i++) {
            double t = (double) i / stepsPerLeg;
            double lng = start.get(0) + t * (mid.get(0) - start.get(0));
            double lat = start.get(1) + t * (mid.get(1) - start.get(1));
            coords.add(List.of(lng, lat));
        }
        for (int i = 0; i <= stepsPerLeg; i++) {
            double t = (double) i / stepsPerLeg;
            double lng = mid.get(0) + t * (end.get(0) - mid.get(0));
            double lat = mid.get(1) + t * (end.get(1) - mid.get(1));
            coords.add(List.of(lng, lat));
        }
        return coords;
    }

    @PreDestroy
    public void cleanup() {
        stopSimulation();
        scheduler.shutdown();
    }
}
