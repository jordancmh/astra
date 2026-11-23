import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { EventLogService } from './event-log.service';
import { AnomalyService } from './anomaly.service';
import { FleetTelemetryService } from './fleet-telemetry.service';
import { BackendNegotiationEvent, LogEntry } from '../models/event-log.model';

describe('EventLogService', () => {
  let service: EventLogService;
  let httpMock: HttpTestingController;
  let anomalyService: AnomalyService;
  let fleetService: FleetTelemetryService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        EventLogService,
        AnomalyService,
        FleetTelemetryService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    anomalyService = TestBed.inject(AnomalyService);
    fleetService = TestBed.inject(FleetTelemetryService);
    service = TestBed.inject(EventLogService);

    // Satisfy initial polling and fleet check requests
    const initialReqs = httpMock.match((req) => req.url.includes('/events') || req.url.includes('/fleet') || req.url.includes('/simulation'));
    for (const req of initialReqs) {
      req.flush([]);
    }
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should initialize with empty events and drawer closed', () => {
    expect(service.events()).toEqual([]);
    expect(service.categoryFilter()).toBe('ALL');
    expect(service.isDrawerOpen()).toBe(false);
    expect(service.totalCount()).toBe(0);
    expect(service.latestEvent()).toBeNull();
  });

  it('should toggle drawer state', () => {
    expect(service.isDrawerOpen()).toBe(false);
    service.toggleDrawer();
    expect(service.isDrawerOpen()).toBe(true);
    service.setDrawerOpen(false);
    expect(service.isDrawerOpen()).toBe(false);
  });

  it('should ingest and deduplicate backend negotiation events', () => {
    const backendEvents: BackendNegotiationEvent[] = [
      {
        id: 'evt-1',
        anomalyId: 'D-001',
        vehicleId: 'V-001',
        affectedRoutes: 'RT-001',
        eventMessage: 'Right-of-way granted to V-001 (Priority 5); V-005 yielding via NPE.',
        createdAt: '2026-11-23T15:30:00Z',
      },
      {
        id: 'evt-2',
        anomalyId: 'D-002',
        vehicleId: 'V-002',
        affectedRoutes: 'RT-002',
        eventMessage: 'Detour recalculated for V-002 avoiding LDP.',
        createdAt: '2026-11-23T15:32:00Z',
      },
    ];

    // Simulate backend response
    service['processBackendEvents'](backendEvents);

    expect(service.events().length).toBe(2);
    expect(service.events()[0].category).toBe('REROUTE'); // newer timestamp
    expect(service.events()[1].category).toBe('NEGOTIATION');

    // Duplicate call with same IDs should not increase count
    service['processBackendEvents'](backendEvents);
    expect(service.events().length).toBe(2);
  });

  it('should capture local AnomalyService events', () => {
    anomalyService.anomalyEvents$.next({
      id: 'local-anom-1',
      eventType: 'INJECTED',
      anomalyId: 'D-003',
      anomalyType: 'ROAD_CLOSURE',
      severity: 'HIGH',
      message: 'ROAD_CLOSURE injected (D-003) affecting 2 routes',
      timestamp: new Date(),
    });

    expect(service.events().length).toBe(1);
    expect(service.events()[0].category).toBe('ANOMALY');
    expect(service.events()[0].severity).toBe('HIGH');
  });

  it('should capture local FleetTelemetryService transition events', () => {
    fleetService.fleetEvents$.next({
      id: 'fleet-evt-1',
      vehicleId: 'V-001',
      eventType: 'REROUTED',
      previousStatus: 'DISRUPTED',
      currentStatus: 'REROUTING',
      message: 'V-001 accepted autonomous detour recalculation',
      timestamp: new Date(),
    });

    expect(service.events().length).toBe(1);
    expect(service.events()[0].category).toBe('REROUTE');
    expect(service.events()[0].vehicleId).toBe('V-001');
  });

  it('should filter events by category', () => {
    service.addEntry({
      id: 'e-1',
      timestamp: new Date(),
      category: 'NEGOTIATION',
      message: 'Negotiation event',
      severity: 'HIGH',
      source: 'BACKEND',
    });
    service.addEntry({
      id: 'e-2',
      timestamp: new Date(),
      category: 'ANOMALY',
      message: 'Anomaly event',
      severity: 'MEDIUM',
      source: 'ANOMALY_SERVICE',
    });

    expect(service.totalCount()).toBe(2);
    expect(service.filteredEvents().length).toBe(2);

    service.setCategoryFilter('NEGOTIATION');
    expect(service.filteredEvents().length).toBe(1);
    expect(service.filteredEvents()[0].category).toBe('NEGOTIATION');

    service.setCategoryFilter('ANOMALY');
    expect(service.filteredEvents().length).toBe(1);
    expect(service.filteredEvents()[0].category).toBe('ANOMALY');

    service.setCategoryFilter('ALL');
    expect(service.filteredEvents().length).toBe(2);
  });

  it('should clear local logs', () => {
    service.addEntry({
      id: 'e-1',
      timestamp: new Date(),
      category: 'SYSTEM',
      message: 'System ready',
      severity: 'LOW',
      source: 'FLEET_SERVICE',
    });
    expect(service.totalCount()).toBe(1);

    service.clearLocalLog();
    expect(service.totalCount()).toBe(0);
    expect(service.events()).toEqual([]);
  });
});
