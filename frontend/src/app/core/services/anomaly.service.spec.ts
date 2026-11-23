import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AnomalyService } from './anomaly.service';
import {
  AnomalyInjectRequest,
  AnomalyInjectResponse,
  AnomalyClearResponse,
  NetworkAnomaly,
  KLANG_VALLEY_BOUNDS,
  AnomalyEvent,
} from '../models/anomaly.model';
import { environment } from '../../../environments/environment';

describe('AnomalyService', () => {
  let service: AnomalyService;
  let httpTesting: HttpTestingController;

  const mockAnomaly: NetworkAnomaly = {
    id: 'D-001',
    anomalyType: 'ROAD_CLOSURE',
    severity: 'HIGH',
    radiusMeters: 600,
    center: [101.6210, 3.1180],
    polygon: {
      type: 'Polygon',
      coordinates: [
        [
          [101.615, 3.112],
          [101.627, 3.112],
          [101.627, 3.124],
          [101.615, 3.124],
          [101.615, 3.112],
        ],
      ],
    },
    injectedAt: '2026-10-12T08:34:12Z',
    isActive: true,
  };

  const mockInjectResponse: AnomalyInjectResponse = {
    anomalyId: 'D-001',
    anomalyType: 'ROAD_CLOSURE',
    severity: 'HIGH',
    affectedRouteIds: ['RT-003'],
    affectedVehicleIds: ['V-003'],
    injectedAt: '2026-10-12T08:34:12Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AnomalyService,
      ],
    });

    service = TestBed.inject(AnomalyService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(service.activeAnomalies()).toEqual([]);
    expect(service.isDroppingOnMap()).toBeFalse();
    expect(service.selectedCoordinates()).toBeNull();
  });

  describe('isWithinKlangValley', () => {
    it('should return true for valid coordinates inside Klang Valley', () => {
      expect(service.isWithinKlangValley(101.6839, 3.1390)).toBeTrue();
      expect(service.isWithinKlangValley(KLANG_VALLEY_BOUNDS.minLng, KLANG_VALLEY_BOUNDS.minLat)).toBeTrue();
      expect(service.isWithinKlangValley(KLANG_VALLEY_BOUNDS.maxLng, KLANG_VALLEY_BOUNDS.maxLat)).toBeTrue();
    });

    it('should return false for coordinates outside Klang Valley', () => {
      // Out-of-bounds latitude
      expect(service.isWithinKlangValley(101.6839, 2.80)).toBeFalse();
      expect(service.isWithinKlangValley(101.6839, 3.50)).toBeFalse();
      // Out-of-bounds longitude
      expect(service.isWithinKlangValley(101.30, 3.1390)).toBeFalse();
      expect(service.isWithinKlangValley(102.00, 3.1390)).toBeFalse();
    });
  });

  describe('fetchActiveAnomalies', () => {
    it('should send GET to /v1/anomalies and update activeAnomalies signal', () => {
      service.fetchActiveAnomalies().subscribe((anomalies) => {
        expect(anomalies).toEqual([mockAnomaly]);
        expect(service.activeAnomalies()).toEqual([mockAnomaly]);
        expect(service.anomalyCount()).toBe(1);
        expect(service.hasActiveAnomalies()).toBeTrue();
      });

      const req = httpTesting.expectOne(`${environment.apiBaseUrl}/v1/anomalies`);
      expect(req.request.method).toBe('GET');
      req.flush([mockAnomaly]);
    });
  });

  describe('injectAnomaly', () => {
    const validRequest: AnomalyInjectRequest = {
      anomalyType: 'ROAD_CLOSURE',
      severity: 'HIGH',
      latitude: 3.1180,
      longitude: 101.6210,
      radiusMeters: 600,
    };

    it('should send POST to /v1/anomalies, emit AnomalyEvent, and refresh active anomalies', () => {
      let receivedEvent: AnomalyEvent | undefined;
      service.anomalyEvents$.subscribe((e) => (receivedEvent = e));

      service.injectAnomaly(validRequest).subscribe((res) => {
        expect(res).toEqual(mockInjectResponse);
        expect(service.lastInjected()).toEqual(mockInjectResponse);
      });

      const injectReq = httpTesting.expectOne(`${environment.apiBaseUrl}/v1/anomalies`);
      expect(injectReq.request.method).toBe('POST');
      expect(injectReq.request.body).toEqual(validRequest);
      injectReq.flush(mockInjectResponse);

      // Verify event was emitted
      expect(receivedEvent).toBeDefined();
      expect(receivedEvent?.eventType).toBe('INJECTED');
      expect(receivedEvent?.anomalyId).toBe('D-001');

      // Verify it automatically re-fetches anomalies
      const fetchReq = httpTesting.expectOne(`${environment.apiBaseUrl}/v1/anomalies`);
      fetchReq.flush([mockAnomaly]);
      expect(service.activeAnomalies().length).toBe(1);
    });

    it('should throw an error and not make HTTP call if coordinates are outside Klang Valley', (done) => {
      const invalidRequest: AnomalyInjectRequest = {
        ...validRequest,
        latitude: 5.000, // Outside bounds
      };

      service.injectAnomaly(invalidRequest).subscribe({
        next: () => fail('Should have failed validation'),
        error: (err) => {
          expect(err.message).toContain('outside Klang Valley');
          httpTesting.expectNone(`${environment.apiBaseUrl}/v1/anomalies`);
          done();
        },
      });
    });
  });

  describe('clearAnomaly', () => {
    it('should send DELETE to /v1/anomalies/{id}, update signal, and emit event', () => {
      // Preload active anomalies
      service.activeAnomalies.set([mockAnomaly]);

      let receivedEvent: AnomalyEvent | undefined;
      service.anomalyEvents$.subscribe((e) => (receivedEvent = e));

      service.clearAnomaly('D-001').subscribe(() => {
        expect(service.activeAnomalies()).toEqual([]);
        expect(service.anomalyCount()).toBe(0);
      });

      const req = httpTesting.expectOne(`${environment.apiBaseUrl}/v1/anomalies/D-001`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expect(receivedEvent).toBeDefined();
      expect(receivedEvent?.eventType).toBe('CLEARED');
      expect(receivedEvent?.anomalyId).toBe('D-001');
    });
  });

  describe('clearAllAnomalies', () => {
    it('should send DELETE to /v1/anomalies, clear signal, and emit ALL_CLEARED event', () => {
      service.activeAnomalies.set([mockAnomaly]);

      const mockClearResponse: AnomalyClearResponse = {
        clearedCount: 1,
        timestamp: '2026-10-12T08:40:00Z',
      };

      let receivedEvent: AnomalyEvent | undefined;
      service.anomalyEvents$.subscribe((e) => (receivedEvent = e));

      service.clearAllAnomalies().subscribe((res) => {
        expect(res).toEqual(mockClearResponse);
        expect(service.activeAnomalies()).toEqual([]);
      });

      const req = httpTesting.expectOne(`${environment.apiBaseUrl}/v1/anomalies`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockClearResponse);

      expect(receivedEvent).toBeDefined();
      expect(receivedEvent?.eventType).toBe('ALL_CLEARED');
    });
  });

  describe('map click picker signal controls', () => {
    it('should update isDroppingOnMap and selectedCoordinates', () => {
      service.setDroppingOnMap(true);
      expect(service.isDroppingOnMap()).toBeTrue();

      service.setSelectedCoordinates([101.6210, 3.1180]);
      expect(service.selectedCoordinates()).toEqual([101.6210, 3.1180]);

      service.setDroppingOnMap(false);
      expect(service.isDroppingOnMap()).toBeFalse();
    });
  });

  describe('generateGeodesicPolygon', () => {
    it('should produce a valid 16-point closed polygon in [lng, lat] format', () => {
      const poly = service.generateGeodesicPolygon(101.6210, 3.1180, 600);
      expect(poly.type).toBe('Polygon');
      expect(poly.coordinates.length).toBe(1);
      const ring = poly.coordinates[0];
      // 16 points + 1 closing point = 17 coordinates
      expect(ring.length).toBe(17);
      expect(ring[0]).toEqual(ring[ring.length - 1]);
      for (const pt of ring) {
        expect(service.isWithinKlangValley(pt[0], pt[1])).toBeTrue();
      }
    });
  });
});
