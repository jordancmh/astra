import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FleetTelemetryService } from './fleet-telemetry.service';
import { FleetStateResponse, SimulationStatus } from '../models/fleet.model';

describe('FleetTelemetryService', () => {
  let service: FleetTelemetryService;
  let httpMock: HttpTestingController;

  const mockResponse: FleetStateResponse = {
    summary: { total: 2, nominal: 1, rerouting: 0, disrupted: 1 },
    vehicles: [
      {
        id: 'V-001',
        routeId: 'RT-001',
        latitude: 3.0833,
        longitude: 101.5833,
        bearing: 90.0,
        speedKmh: 60.0,
        cargoPriority: 5,
        status: 'DISRUPTED',
        intersectedAnomalyId: 'D-001',
        updatedAt: '2026-11-23T14:30:00Z',
      },
      {
        id: 'V-002',
        routeId: 'RT-002',
        latitude: 3.1085,
        longitude: 101.6035,
        bearing: 180.0,
        speedKmh: 55.0,
        cargoPriority: 4,
        status: 'NOMINAL',
        intersectedAnomalyId: null,
        updatedAt: '2026-11-23T14:30:00Z',
      },
    ],
    routes: [
      {
        id: 'RT-001',
        vehicleId: 'V-001',
        status: 'OPTIMAL',
        geometry: {
          type: 'LineString',
          coordinates: [
            [101.58, 3.08],
            [101.60, 3.10],
          ],
        },
        distanceMeters: 5000,
        estimatedTimeMs: 300000,
      },
    ],
    timestamp: '2026-11-23T14:30:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FleetTelemetryService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(FleetTelemetryService);
    httpMock = TestBed.inject(HttpTestingController);

    // Drain initial constructor calls
    const initialReqs = httpMock.match((req) => req.url.includes('/api/v1/'));
    initialReqs.forEach((r) => r.flush(null));
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should initialize with default signals', () => {
    expect(service.vehicles()).toEqual([]);
    expect(service.routes()).toEqual([]);
    expect(service.summary().total).toBe(0);
    expect(service.selectedVehicleId()).toBeNull();
    expect(service.isSimulationRunning()).toBeFalse();
  });

  it('should update signals on fetchFleetState', () => {
    service.fetchFleetState().subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/fleet');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(service.vehicles().length).toBe(2);
    expect(service.routes().length).toBe(1);
    expect(service.summary().total).toBe(2);
    expect(service.summary().disrupted).toBe(1);
    expect(service.lastSyncedAt()).not.toBeNull();
  });

  it('should filter vehicles reactively by status', () => {
    service.fetchFleetState().subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/fleet');
    req.flush(mockResponse);

    expect(service.filteredVehicles().length).toBe(2);

    service.setStatusFilter('DISRUPTED');
    expect(service.filteredVehicles().length).toBe(1);
    expect(service.filteredVehicles()[0].id).toBe('V-001');

    service.setStatusFilter('NOMINAL');
    expect(service.filteredVehicles().length).toBe(1);
    expect(service.filteredVehicles()[0].id).toBe('V-002');
  });

  it('should select vehicle and compute selectedVehicle signal', () => {
    service.fetchFleetState().subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/fleet');
    req.flush(mockResponse);

    service.selectVehicle('V-001');
    expect(service.selectedVehicle()?.id).toBe('V-001');
    expect(service.selectedVehicle()?.cargoPriority).toBe(5);

    service.selectVehicle(null);
    expect(service.selectedVehicle()).toBeNull();
  });

  it('should trigger startSimulation and pauseSimulation', () => {
    service.startSimulation().subscribe();
    const startReq = httpMock.expectOne('http://localhost:8080/api/v1/simulation/start');
    startReq.flush({ running: true, vehicleCount: 5, tickCount: 1, message: 'Running' } as SimulationStatus);
    expect(service.isSimulationRunning()).toBeTrue();

    service.pauseSimulation().subscribe();
    const stopReq = httpMock.expectOne('http://localhost:8080/api/v1/simulation/stop');
    stopReq.flush({ running: false, vehicleCount: 5, tickCount: 5, message: 'Paused' } as SimulationStatus);
    expect(service.isSimulationRunning()).toBeFalse();
  });
});
